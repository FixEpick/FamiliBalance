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
//   title_key/body_key: string,   // имена string-ресурсов для локализации на клиенте
//   title_args/body_args: string, // JSON-массив строк-аргументов шаблона
// }
//
// Если переданы title_key/body_key — сообщение уходит data-only (без
// android.notification): локализация происходит на устройстве получателя
// в FamiliBalanceMessagingService. Без ключей — старое поведение с
// notification-блоком (fallback-текст на языке отправителя).
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
  title_key?: string;
  body_key?: string;
  title_args?: string;
  body_args?: string;
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

  const { target_user_id, target_role, title, body, screen, channel, priority,
          title_key, body_key, title_args, body_args } = payload;
  if (!target_user_id || !target_role || !title) {
    return jsonResponse({ error: "missing_fields" }, 400);
  }
  if (target_role !== "child" && target_role !== "parent") {
    return jsonResponse({ error: "invalid_role" }, 400);
  }

  // Авторизация вызывающего: функция доступна любому держателю anon-ключа,
  // поэтому проверяем, что caller реально связан с target_user_id.
  // Без этой проверки любой мог слать произвольные пуши чужим устройствам
  // (спам/фишинг под видом уведомлений приложения).
  const authHeader = req.headers.get("Authorization") ?? "";
  const callerJwt = authHeader.startsWith("Bearer ")
    ? authHeader.slice(7)
    : "";
  if (!callerJwt) {
    return jsonResponse({ error: "unauthorized" }, 401);
  }
  const callerClient = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!
  );
  const { data: { user: caller } } = await callerClient.auth.getUser(callerJwt);
  if (!caller) {
    return jsonResponse({ error: "unauthorized" }, 401);
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
  );

  // Разрешённые связи caller → target:
  //  - parent-target: target_user_id == caller.id (свой кабинет)
  //    ИЛИ caller — ребёнок этого родителя (auth_user_id → parent_user_id);
  //  - child-target:  family_profiles.user_id == target_user_id И
  //    (parent_user_id == caller.id — родитель пишет ребёнку
  //     ИЛИ auth_user_id == caller.id — устройство ребёнка пишет себе
  //     ИЛИ user_id == caller.id — legacy uid).
  // Родитель всегда может писать самому себе — не требует связей в БД.
  if (target_role === "parent" && target_user_id === caller.id) {
    // пропускаем проверку связей
  } else {
  const { data: linkRows, error: linkError } = await supabase
    .from("family_profiles")
    .select("user_id, parent_user_id, auth_user_id")
    .or(
      `user_id.eq.${target_user_id},parent_user_id.eq.${caller.id},auth_user_id.eq.${caller.id}`
    );
  if (linkError) {
    console.error("authorization lookup failed:", linkError);
    return jsonResponse({ error: "db_error" }, 500);
  }
  const allowed = (linkRows ?? []).some((row: {
    user_id: string;
    parent_user_id: string | null;
    auth_user_id: string | null;
  }) => {
    if (target_role === "parent") {
      return target_user_id === caller.id ||
        (row.auth_user_id === caller.id && row.parent_user_id === target_user_id);
    }
    return row.user_id === target_user_id &&
      (row.parent_user_id === caller.id ||
        row.auth_user_id === caller.id ||
        row.user_id === caller.id);
  });
  if (!allowed) {
    return jsonResponse({ error: "forbidden" }, 403);
  }
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

  // Токены целевой роли/пользователя (service-role клиент создан выше).
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
  // Ключи локализации → data-only: текст собирается на устройстве
  // получателя в его локали (включая фон — onMessageReceived вызывается
  // и для свёрнутого приложения при high priority).
  const localized = Boolean(title_key || body_key);

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
          ...(title_key ? { title_key } : {}),
          ...(body_key ? { body_key } : {}),
          ...(title_args ? { title_args } : {}),
          ...(body_args ? { body_args } : {}),
        },
        android: {
          priority: highPriority ? "HIGH" : "NORMAL",
          // notification-блок только для legacy-отправителей без ключей:
          // при data-only FCM доставляет сообщение в onMessageReceived,
          // где текст локализуется на устройстве получателя.
          ...(localized ? {} : {
            notification: {
              title,
              body: body ?? "",
              channel_id: channel ?? "quests_and_rewards",
              sound: "default",
            },
          }),
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
