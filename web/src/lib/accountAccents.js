// Keep the palette and assignment in sync with Android's AccountAccents.
const palette = [
  ["#087c73", "#52d6c5"],
  ["#205cb5", "#8cb8ff"],
  ["#514db4", "#aaa9ff"],
  ["#7848a6", "#c9a2f2"],
  ["#597320", "#b7d97e"],
  ["#826500", "#e5c465"],
  ["#257849", "#79d49b"],
  ["#54647b", "#aabbd1"],
  ["#126f8e", "#6ccdeb"],
];

export function accountAccents(names) {
  const accents = new Map();
  const used = Array(palette.length).fill(0);
  for (const name of [...new Set(names)].sort()) {
    let hash = 0;
    for (let i = 0; i < name.length; i++) hash = (31 * hash + name.charCodeAt(i)) | 0;
    const preferred = ((hash % palette.length) + palette.length) % palette.length;
    let index = preferred;
    for (let offset = 1; offset < palette.length; offset++) {
      const candidate = (preferred + offset) % palette.length;
      if (used[candidate] < used[index]) index = candidate;
    }
    used[index]++;
    const [light, dark] = palette[index];
    accents.set(name, { "--account-accent-light": light, "--account-accent-dark": dark });
  }
  return accents;
}
