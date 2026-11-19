// Supabase Edge Function: send-push
//
// Принимает от клиента событие пуша, находит fcm_token'ы в device_tokens
// и отправляет через Firebase Cloud Messaging HTTP v1 API.
// Ключи Firebase НЕ покидают сервер: service account хранится в secrets.
//
// Body: {
//   target_user_id: string,   // child profile id или parent auth uid
//   target_role:    "child" | "parent",
//   title:          string,
//   body:           string,
//   screen:         string,   // deep-link: main|tasks|history|verification|progress
//   channel:        string,   // quests_and_rewards|parent_moderation|balance_updates
//   priority:       "high" | "normal"
// }
//
// Secrets (supabase secrets set):
//   FCM_SERVICE_ACCOUNT — JSON service account Firebase (одной строкой)
//
// Deploy: supabase functions deploy send-push

import { createClient } from "jsr:@supabase/supabase-js@2";
import { SignJWT, importPKCS8 } from "npm:jose@5";

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const FCM_TOKEN_URL = "https://oauth2.googleapis.com/token";

interface PushRequest {
  target_user_id?: string;
  target_role?: string;
  title?: string;
  body?: string;
  screen?: string;
  channel?: string;
  priority?: string;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** OAuth2 access token для FCM HTTP v1 (JWT bearer → access_token). */
async function getFcmAccessToken(sa: {
  client_email: string;
  private_key: string;
  project_id: string;
}): Promise<string> {
  const key = await importPKCS8(sa.private_key, "RS256");
  const now = Math.floor(Date.now() / 1000);
  const jwt = await new SignJWT({ scope: FCM_SCOPE })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(sa.client_email)
    .setSubject(sa.client_email)
    .setAudience(FCM_TOKEN_URL)
    .setIssuedAt(now)
    .setExpirationTime(now + 3600)
    .sign(key);

  const res = await fetch(FCM_TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!res.ok) throw new Error(`FCM token request failed: ${res.status} ${await res.text()}`);
  const data = await res.json();
  return data.access_token as string;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return jsonResponse({ error: "method_not_allowed" }, 405);

  let payload: PushRequest;
  try {
    payload = await req.json();
  } catch {
    return jsonResponse({ error: "invalid_json" }, 400);
  }

  const { target_user_id, target_role, title, body, screen, channel, priority } = payload;
  if (!target_user_id || !target_role || !title) {
    return jsonResponse({ error: "missing_fields" }, 400);
  }
  if (target_role !== "child" && target_role !== "parent") {
    return jsonResponse({ error: "invalid_role" }, 400);
  }

  // Service account из secrets — без него пуши не отправить.
  const saRaw = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!saRaw) {
    console.error("FCM_SERVICE_ACCOUNT secret is not set");
    return jsonResponse({ error: "fcm_not_configured" }, 500);
  }
  const sa = JSON.parse(saRaw) as {
    client_email: string;
    private_key: string;
    project_id: string;
  };

  // Токены целевой роли/пользователя.
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
  );
  const { data: tokens, error } = await supabase
    .from("device_tokens")
    .select("fcm_token")
    .eq("user_id", target_user_id)
    .eq("role", target_role);
  if (error) {
    console.error("device_tokens query failed:", error);
    return jsonResponse({ error: "db_error" }, 500);
  }
  if (!tokens || tokens.length === 0) {
    return jsonResponse({ sent: 0, reason: "no_tokens" });
  }

  const accessToken = await getFcmAccessToken(sa);
  const fcmUrl =
    `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;
  const highPriority = priority === "high";

  let sent = 0;
  const staleTokens: string[] = [];

  for (const { fcm_token } of tokens) {
    const message = {
      message: {
        token: fcm_token,
        data: {
          title,
          body: body ?? "",
          screen: screen ?? "",
          role: target_role,
          channel: channel ?? "",
          priority: highPriority ? "high" : "normal",
        },
        android: {
          priority: highPriority ? "HIGH" : "NORMAL",
          notification: {
            // title/body обязательны: когда приложение свёрнуто, FCM SDK
            // показывает уведомление сам по этому блоку — без них получается
            // пустое «Expand to view». В foreground onMessageReceived
            // получает data и строит своё уведомление.
            title,
            body: body ?? "",
            channel_id: channel ?? "quests_and_rewards",
            sound: "default",
          },
        },
      },
    };
    const res = await fetch(fcmUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify(message),
    });
    if (res.ok) {
      sent++;
    } else {
      const errText = await res.text();
      console.error(`FCM send failed for token ${fcm_token.slice(0, 12)}…: ${res.status} ${errText}`);
      // UNREGISTERED / INVALID_ARGUMENT — токен мёртв, чистим.
      if (res.status === 404 || errText.includes("UNREGISTERED") || errText.includes("INVALID_ARGUMENT")) {
        staleTokens.push(fcm_token);
      }
    }
  }

  // Чистим протухшие токены, чтобы не копить мусор.
  if (staleTokens.length > 0) {
    await supabase
      .from("device_tokens")
      .delete()
      .eq("user_id", target_user_id)
      .eq("role", target_role)
      .in("fcm_token", staleTokens);
  }

  return jsonResponse({ sent, total: tokens.length });
});
