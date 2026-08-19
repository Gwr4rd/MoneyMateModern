const CONFIG_KEY = "moneymate-supabase-config";
export const SYNC_PENDING_KEY = "moneymate-supabase-pending";
export const SYNC_REMOTE_KEY = "moneymate-supabase-remote-updated-at";
export const SYNC_CONFLICT_KEY = "moneymate-supabase-conflict";

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
  const current = getSupabaseConfig();
  const changed = current.url !== url || current.key !== key;
  localStorage.setItem(CONFIG_KEY, JSON.stringify({ url, key }));
  if (changed) {
    clientPromise = undefined;
    clientSignature = "";
    localStorage.removeItem(SYNC_REMOTE_KEY);
    localStorage.removeItem(SYNC_PENDING_KEY);
    localStorage.removeItem(SYNC_CONFLICT_KEY);
  }
  return { url, key, source: "local", changed };
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
    clientPromise = import("@supabase/supabase-js").then(({ createClient }) => createClient(url, key, {
      auth: {
        persistSession: true,
        autoRefreshToken: true,
        detectSessionInUrl: true,
      },
    }));
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
  return data;
}

export async function getCurrentUser() {
  if (!isSupabaseConfigured()) return null;
  const client = await getClient();
  const { data, error } = await client.auth.getSession();
  if (error) throw error;
  return data.session?.user || null;
}

export async function signOut() {
  const client = await getClient();
  const { error } = await client.auth.signOut();
  if (error) throw error;
  localStorage.removeItem(SYNC_PENDING_KEY);
  localStorage.removeItem(SYNC_REMOTE_KEY);
  localStorage.removeItem(SYNC_CONFLICT_KEY);
}

async function requireSession() {
  const client = await getClient();
  const { data, error } = await client.auth.getSession();
  if (error) throw error;
  const user = data.session?.user;
  if (!user) throw new Error("La sesion termino. Inicia sesion nuevamente.");
  return { client, user };
}

export async function uploadSnapshot(snapshot) {
  const { client, user } = await requireSession();
  const { data, error } = await client
    .from("money_snapshots")
    .upsert({ user_id: user.id, data: snapshot }, { onConflict: "user_id" })
    .select("updated_at")
    .single();
  if (error) throw error;
  return data;
}

export async function downloadSnapshot() {
  const { client, user } = await requireSession();
  const { data, error } = await client
    .from("money_snapshots")
    .select("data, updated_at")
    .eq("user_id", user.id)
    .maybeSingle();
  if (error) throw error;
  if (!data) throw new Error("Todavia no existe una copia en Supabase.");
  return data;
}
