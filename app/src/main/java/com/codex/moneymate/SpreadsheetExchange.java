package com.codex.moneymate;

import android.content.Context;
import android.net.Uri;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class SpreadsheetExchange {
    private static final String TRANSFER = "Transferencia";
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private SpreadsheetExchange() {
    }

    static ImportResult importXlsx(Context context, Uri uri, MoneyDb target, String defaultCurrency) throws Exception {
        byte[] sheet = null;
        byte[] sharedStrings = null;
        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IllegalArgumentException("No se pudo abrir el Excel.");
            try (ZipInputStream zip = new ZipInputStream(raw)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if ("xl/worksheets/sheet1.xml".equals(name)) {
                        sheet = readAll(zip);
                    } else if ("xl/sharedStrings.xml".equals(name)) {
                        sharedStrings = readAll(zip);
                    }
                }
            }
        }
        if (sheet == null) throw new IllegalArgumentException("El Excel no tiene una hoja legible.");

        List<String> shared = sharedStrings == null ? new ArrayList<>() : parseSharedStrings(sharedStrings);
        List<List<String>> rows = parseSheet(sheet, shared);
        if (rows.size() < 2) throw new IllegalArgumentException("El Excel no contiene movimientos.");

        Map<String, Integer> index = headerIndex(rows.get(0));
        List<ImportedAccount> accounts = new ArrayList<>();
        List<ImportedCategory> categories = new ArrayList<>();
        List<ImportedTxn> txns = new ArrayList<>();
        String currency = clean(defaultCurrency, "USD");

        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String rawDate = cell(row, index, "fecha", "date", "datetime");
            if (rawDate.trim().isEmpty()) continue;
            String date = normalizeDateForImport(rawDate);
            String time = firstNonEmpty(cell(row, index, "hora", "time"), normalizeTimeForImport(rawDate));
            String account = firstNonEmpty(cell(row, index, "cuenta", "account"), "Cuenta");
            String category = firstNonEmpty(cell(row, index, "categoria", "category"), "Sin categoria");
            String rawKind = cell(row, index, "ingresogasto", "tipo", "kind", "incomeexpense");
            String amountText = firstNonEmpty(
                    cell(row, index, "importe", "monto", "amount"),
                    cell(row, index, key(currency), "pen", "usd", "eur", "mxn", "cop", "clp", "ars", "brl")
            );
            double amount = parseAmount(amountText);
            if (amount <= 0) continue;
            String note = cell(row, index, "nota", "note", "memo");
            String description = cell(row, index, "descripcion", "description");
            String rowCurrency = firstNonEmpty(cell(row, index, "moneda", "currency"), currency);

            if (isTransferKind(rawKind)) {
                txns.add(new ImportedTxn(date, time, account, TRANSFER, "expense", amount, note, description));
                if (!category.trim().isEmpty() && !account.equals(category)) {
                    txns.add(new ImportedTxn(date, time, category, TRANSFER, "income", amount, note, description));
                }
            } else {
                txns.add(new ImportedTxn(date, time, account, category, normalizeKind(rawKind), amount, note, description));
            }
            if (!containsAccount(accounts, account)) accounts.add(new ImportedAccount(account, 0, rowCurrency, typeFor(account), "", true, false));
            if (isTransferKind(rawKind) && !"Sin categoria".equals(category) && !containsAccount(accounts, category)) accounts.add(new ImportedAccount(category, 0, rowCurrency, typeFor(category), "", true, false));
        }

        completeLists(accounts, categories, txns, currency);
        target.replaceFromImport(accounts, categories, txns);
        return new ImportResult(accounts.size(), categories.size(), txns.size());
    }

    static void exportXlsx(Context context, Uri uri, MoneyDb db, String currencyCode) throws Exception {
        exportXlsx(context, uri, db.allTransactions(), currencyCode, "Registro Contable");
    }

    static void exportXlsx(Context context, Uri uri, List<MoneyDb.Row> transactions, String currencyCode, String sheetTitle) throws Exception {
        try (OutputStream raw = context.getContentResolver().openOutputStream(uri)) {
            if (raw == null) throw new IllegalArgumentException("No se pudo guardar el Excel.");
            try (ZipOutputStream zip = new ZipOutputStream(raw)) {
                writeEntry(zip, "[Content_Types].xml", contentTypesXml());
                writeEntry(zip, "_rels/.rels", rootRelsXml());
                writeEntry(zip, "xl/workbook.xml", workbookXml(sheetTitle));
                writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml());
                writeEntry(zip, "xl/styles.xml", stylesXml());
                writeEntry(zip, "xl/worksheets/sheet1.xml", sheetXml(transactions, clean(currencyCode, "USD")));
            }
        }
    }

    static void exportXls(Context context, Uri uri, List<MoneyDb.Row> transactions, String currencyCode, String reportTitle) throws Exception {
        OutputStream raw = context.getContentResolver().openOutputStream(uri);
        if (raw == null) throw new IllegalArgumentException("No se pudo guardar el Excel.");
        try (OutputStream output = raw;
             Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write(legacyWorkbookXml(transactions, clean(currencyCode, "USD"), reportTitle));
        }
    }

    static String normalizeKind(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if (lower.contains("ingreso") || lower.contains("income") || lower.contains("deposit")) return "income";
        return "expense";
    }

    static boolean isTransferKind(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.US);
        return lower.contains("transfer") || lower.contains("dinero gastado") || lower.contains("entre cuentas");
    }

    static String normalizeDateForImport(String value) {
        String v = clean(value, "");
        if (v.isEmpty()) return "";
        if (v.matches("\\d{4}-\\d{2}-\\d{2}.*")) return v.substring(0, 10);
        if (v.matches("\\d{1,2}/\\d{1,2}/\\d{4}.*")) {
            String[] parts = v.split("[ /:]");
            return String.format(Locale.US, "%04d-%02d-%02d", Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
        }
        double serial = parseAmount(v);
        if (serial > 1000) {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            fmt.setTimeZone(UTC);
            return fmt.format(excelDate(serial).getTime());
        }
        return v.length() > 10 ? v.substring(0, 10) : v;
    }

    static String normalizeTimeForImport(String value) {
        String v = clean(value, "");
        if (v.matches("\\d{4}-\\d{2}-\\d{2}.+\\d{2}:\\d{2}.*")) return v.substring(11, 16);
        if (v.matches("\\d{1,2}:\\d{2}.*")) return v.substring(0, 5);
        double serial = parseAmount(v);
        if (serial > 1000) {
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.US);
            fmt.setTimeZone(UTC);
            return fmt.format(excelDate(serial).getTime());
        }
        return "00:00";
    }

    static double parseAmount(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isEmpty()) return 0;
        raw = raw.replace("S/", "").replace("$", "").replace("PEN", "").replace("USD", "").replace("EUR", "");
        raw = raw.replaceAll("[^0-9,.-]", "");
        if (raw.indexOf(',') >= 0 && raw.indexOf('.') < 0) raw = raw.replace(',', '.');
        else raw = raw.replace(",", "");
        try {
            return Math.abs(Double.parseDouble(raw));
        } catch (Exception ignored) {
            return 0;
        }
    }

    static String key(String value) {
        String lower = clean(value, "").toLowerCase(Locale.US)
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u").replace("ñ", "n");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) out.append(ch);
        }
        return out.toString();
    }

    private static List<String> parseSharedStrings(byte[] xml) throws Exception {
        List<String> out = new ArrayList<>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new ByteArrayInputStream(xml), "UTF-8");
        StringBuilder current = null;
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            String name = parser.getName();
            if (event == XmlPullParser.START_TAG && "si".equals(name)) {
                current = new StringBuilder();
            } else if (event == XmlPullParser.START_TAG && "t".equals(name) && current != null) {
                current.append(parser.nextText());
            } else if (event == XmlPullParser.END_TAG && "si".equals(name)) {
                out.add(current == null ? "" : current.toString());
                current = null;
            }
        }
        return out;
    }

    private static List<List<String>> parseSheet(byte[] xml, List<String> shared) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new ByteArrayInputStream(xml), "UTF-8");
        List<String> row = null;
        boolean inCell = false;
        int cellColumn = 0;
        String cellType = "";
        StringBuilder cellValue = null;
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            String name = parser.getName();
            if (event == XmlPullParser.START_TAG && "row".equals(name)) {
                row = new ArrayList<>();
            } else if (event == XmlPullParser.START_TAG && "c".equals(name)) {
                inCell = true;
                cellColumn = columnIndex(attr(parser, "r"));
                cellType = clean(attr(parser, "t"), "");
                cellValue = new StringBuilder();
            } else if (event == XmlPullParser.START_TAG && inCell && ("v".equals(name) || "t".equals(name))) {
                cellValue.append(parser.nextText());
            } else if (event == XmlPullParser.END_TAG && "c".equals(name)) {
                if (row != null) {
                    while (row.size() <= cellColumn) row.add("");
                    row.set(cellColumn, resolveCell(cellType, cellValue == null ? "" : cellValue.toString(), shared));
                }
                inCell = false;
            } else if (event == XmlPullParser.END_TAG && "row".equals(name)) {
                if (row != null && !emptyRow(row)) rows.add(trimRow(row));
                row = null;
            }
        }
        return rows;
    }

    private static Map<String, Integer> headerIndex(List<String> headers) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String k = key(headers.get(i));
            if (!k.isEmpty() && !out.containsKey(k)) out.put(k, i);
        }
        return out;
    }

    private static void completeLists(List<ImportedAccount> accounts, List<ImportedCategory> categories, List<ImportedTxn> txns, String currency) {
        for (ImportedTxn t : txns) {
            if (!containsAccount(accounts, t.account)) accounts.add(new ImportedAccount(t.account, 0, currency, typeFor(t.account), "", true, false));
        }
        Map<String, Boolean> categoryNames = new LinkedHashMap<>();
        for (ImportedCategory c : categories) categoryNames.put(c.kind + "|" + c.name, true);
        for (ImportedTxn t : txns) {
            String k = t.kind + "|" + t.category;
            if (!categoryNames.containsKey(k)) {
                categories.add(new ImportedCategory(t.category, t.kind, ""));
                categoryNames.put(k, true);
            }
        }
    }

    private static boolean containsAccount(List<ImportedAccount> accounts, String name) {
        for (ImportedAccount a : accounts) if (a.name.equals(name)) return true;
        return false;
    }

    private static String typeFor(String account) {
        String lower = clean(account, "").toLowerCase(Locale.US);
        return lower.contains("efectivo") || lower.contains("cash") || lower.equals("ahorro s/") || lower.equals("ahorro s") || lower.startsWith("ahorro s/")
                ? "Efectivo"
                : "Cuentas de Banco";
    }

    private static String cell(List<String> row, Map<String, Integer> index, String... keys) {
        for (String k : keys) {
            Integer i = index.get(key(k));
            if (i != null && i < row.size()) return clean(row.get(i), "");
        }
        return "";
    }

    private static Calendar excelDate(double serial) {
        int days = (int) Math.floor(serial);
        int millis = (int) Math.round((serial - days) * 86400000d);
        Calendar cal = Calendar.getInstance(UTC);
        cal.clear();
        cal.set(1899, Calendar.DECEMBER, 30, 0, 0, 0);
        cal.add(Calendar.DATE, days);
        cal.add(Calendar.MILLISECOND, millis);
        return cal;
    }

    private static String resolveCell(String type, String raw, List<String> shared) {
        String value = clean(raw, "");
        if ("s".equals(type)) {
            try {
                int index = Integer.parseInt(value);
                return index >= 0 && index < shared.size() ? shared.get(index) : "";
            } catch (Exception ignored) {
                return "";
            }
        }
        return value;
    }

    private static String attr(XmlPullParser parser, String name) {
        String value = parser.getAttributeValue(null, name);
        return value == null ? "" : value;
    }

    private static int columnIndex(String ref) {
        int index = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = Character.toUpperCase(ref.charAt(i));
            if (ch < 'A' || ch > 'Z') break;
            index = index * 26 + (ch - 'A' + 1);
        }
        return Math.max(index - 1, 0);
    }

    private static boolean emptyRow(List<String> row) {
        for (String value : row) if (!clean(value, "").isEmpty()) return false;
        return true;
    }

    private static List<String> trimRow(List<String> row) {
        int last = row.size() - 1;
        while (last >= 0 && clean(row.get(last), "").isEmpty()) last--;
        return new ArrayList<>(row.subList(0, last + 1));
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    private static void writeEntry(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String sheetXml(List<MoneyDb.Row> transactions, String currency) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Fecha", "Cuenta", "Categoría", "Subcategorías", "Nota", currency, "Ingreso/Gasto", "Descripción", "Importe", "Moneda"});
        for (MoneyDb.Row r : transactions) {
            boolean unifiedTransfer = "transfer".equals(r.kind);
            String kind = unifiedTransfer ? "Transferencia" : (TRANSFER.equals(r.category) && "expense".equals(r.kind) ? "Dinero gastado" : ("income".equals(r.kind) ? "Ingreso" : "Gasto"));
            String date = r.date + "T" + clean(r.time, "00:00") + ":00";
            String amount = String.format(Locale.US, "%.2f", r.amount);
            String account = unifiedTransfer ? r.transferFrom : r.account;
            String category = unifiedTransfer ? r.transferTo : r.category;
            rows.add(new String[]{date, account, category, "", r.note, amount, kind, r.description, amount, currency});
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            sb.append("<row r=\"").append(r + 1).append("\">");
            String[] row = rows.get(r);
            for (int c = 0; c < row.length; c++) {
                String ref = columnName(c + 1) + (r + 1);
                boolean numeric = r > 0 && (c == 5 || c == 8);
                String value = clean(row[c], "");
                if (numeric) {
                    sb.append("<c r=\"").append(ref).append("\"><v>").append(xml(value)).append("</v></c>");
                } else {
                    sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t");
                    if (!value.equals(value.trim())) sb.append(" xml:space=\"preserve\"");
                    sb.append(">").append(xml(value)).append("</t></is></c>");
                }
            }
            sb.append("</row>");
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static String columnName(int index) {
        StringBuilder out = new StringBuilder();
        while (index > 0) {
            int rem = (index - 1) % 26;
            out.insert(0, (char) ('A' + rem));
            index = (index - rem - 1) / 26;
        }
        return out.toString();
    }

    private static String legacyWorkbookXml(List<MoneyDb.Row> transactions, String currency, String reportTitle) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<?mso-application progid=\"Excel.Sheet\"?>");
        sb.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" ");
        sb.append("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">");
        sb.append("<Styles><Style ss:ID=\"Header\"><Font ss:Bold=\"1\"/><Interior ss:Color=\"#DFF4E8\" ss:Pattern=\"Solid\"/></Style>");
        sb.append("<Style ss:ID=\"Money\"><NumberFormat ss:Format=\"0.00\"/></Style></Styles>");
        sb.append("<Worksheet ss:Name=\"").append(xml(clean(reportTitle, "Reporte"))).append("\"><Table>");
        String[] headers = {"Fecha", "Hora", "Tipo", "Cuenta origen", "Cuenta destino o categoria", "Nota", "Descripcion", "Importe", "Moneda"};
        sb.append("<Row>");
        for (String header : headers) {
            sb.append("<Cell ss:StyleID=\"Header\"><Data ss:Type=\"String\">").append(xml(header)).append("</Data></Cell>");
        }
        sb.append("</Row>");
        for (MoneyDb.Row row : transactions) {
            boolean transfer = "transfer".equals(row.kind);
            String type = transfer ? "Transferencia" : ("income".equals(row.kind) ? "Ingreso" : "Gasto");
            String origin = transfer ? row.transferFrom : row.account;
            String destination = transfer ? row.transferTo : row.category;
            sb.append("<Row>");
            legacyCell(sb, row.date, "String", null);
            legacyCell(sb, row.time, "String", null);
            legacyCell(sb, type, "String", null);
            legacyCell(sb, origin, "String", null);
            legacyCell(sb, destination, "String", null);
            legacyCell(sb, row.note, "String", null);
            legacyCell(sb, row.description, "String", null);
            legacyCell(sb, String.format(Locale.US, "%.2f", row.amount), "Number", "Money");
            legacyCell(sb, currency, "String", null);
            sb.append("</Row>");
        }
        sb.append("</Table></Worksheet></Workbook>");
        return sb.toString();
    }

    private static void legacyCell(StringBuilder out, String value, String type, String style) {
        out.append("<Cell");
        if (style != null) out.append(" ss:StyleID=\"").append(style).append("\"");
        out.append("><Data ss:Type=\"").append(type).append("\">");
        out.append(xml(clean(value, "")));
        out.append("</Data></Cell>");
    }

    private static String contentTypesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "</Types>";
    }

    private static String rootRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String workbookXml(String title) {
        String safeTitle = clean(title, "Registro Contable");
        if (safeTitle.length() > 31) safeTitle = safeTitle.substring(0, 31);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + xml(safeTitle) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + "</workbook>";
    }

    private static String workbookRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>";
    }

    private static String stylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                + "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>"
                + "</styleSheet>";
    }

    private static String xml(String value) {
        return clean(value, "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String firstNonEmpty(String preferred, String fallback) {
        return preferred != null && !preferred.trim().isEmpty() ? preferred : clean(fallback, "");
    }

    private static String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
