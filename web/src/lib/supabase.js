const CONFIG_KEY = "moneymate-supabase-config";

let clientPromise;
let clientSignature = "";

export function getSupabaseConfig() {
  try {
    const saved = JSON.parse(localStorage.getItem(CONFIG_KEY));
    if (saved?.url && saved?.key) return { url: saved.url, key: saved.key, source: "local" };
  } catch {
    // Fall through to optional deployment defaults.
  }
  const url = import.meta.env.VITE_SUPABASE_URL || "";
  const key = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY || "";
  return { url, key, source: url && key ? "environment" : "none" };
}

export function saveSupabaseConfig(urlValue, keyValue) {
  const url = String(urlValue || "").trim().replace(/\/+$/, "");
  const key = String(keyValue || "").trim();
  if (!url.startsWith("https://") || !key) {
    throw new Error("Ingresa una URL https y la clave publica de Supabase.");
  }
  localStorage.setItem(CONFIG_KEY, JSON.stringify({ url, key }));
  clientPromise = undefined;
  clientSignature = "";
  return { url, key, source: "local" };
}

export function isSupabaseConfigured() {
  const { url, key } = getSupabaseConfig();
  return Boolean(url && key);
}

async function getClient() {
  const { url, key } = getSupabaseConfig();
  if (!url || !key) throw new Error("Primero configura y guarda la conexion de Supabase.");
  const signature = `${url}|${key}`;
  if (!clientPromise || clientSignature !== signature) {
    clientSignature = signature;
    clientPromise = import("@supabase/supabase-js").then(({ createClient }) => createClient(url, key));
  }
  return clientPromise;
}

export async function createAccount(email, password) {
  const client = await getClient();
  const { data, error } = await client.auth.signUp({ email, password });
  if (error) throw error;
  return data;
}

export async function signIn(email, password) {
  const client = await getClient();
  const { data, error } = await client.auth.signInWithPassword({ email, password });
  if (error) throw error;
  return { client, user: data.user };
}

export async function uploadSnapshot(email, password, snapshot) {
  const { client, user } = await signIn(email, password);
  const { error } = await client
    .from("money_snapshots")
    .upsert({ user_id: user.id, data: snapshot }, { onConflict: "user_id" });
  if (error) throw error;
}

export async function downloadSnapshot(email, password) {
  const { client, user } = await signIn(email, password);
  const { data, error } = await client
    .from("money_snapshots")
    .select("data, updated_at")
    .eq("user_id", user.id)
    .single();
  if (error) throw error;
  return data;
}
