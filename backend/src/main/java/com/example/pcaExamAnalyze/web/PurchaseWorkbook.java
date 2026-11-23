package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.service.ClassPackageService.BulkPurchase;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

final class PurchaseWorkbook {
    private static final List<String> HEADERS = List.of("First Name", "Reg ID", "Phone Number", "School", "District");
    record WorkbookRow(int number, List<String> values) {}

    static byte[] template() throws IOException {
        return template("Confirmed Purchases", HEADERS);
    }

    static byte[] template(String name, List<String> headers) throws IOException {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(name);
            var header = sheet.createRow(0);
            var text = workbook.createCellStyle();
            text.setDataFormat(workbook.createDataFormat().getFormat("@"));
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
                sheet.setColumnWidth(i, (i == 3 ? 42 : 22) * 256);
                sheet.setDefaultColumnStyle(i, text);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    static List<BulkPurchase> read(MultipartFile file) {
        return read(file, HEADERS, Integer.MAX_VALUE, "student").stream()
            .map(row -> new BulkPurchase(row.number(), row.values().get(0), row.values().get(1),
                row.values().get(2), row.values().get(3), row.values().get(4))).toList();
    }

    static List<WorkbookRow> read(MultipartFile file, List<String> headers, int maxRows, String rowLabel) {
        return read(file, headers, List.of(), maxRows, rowLabel);
    }

    static List<WorkbookRow> read(MultipartFile file, List<String> headers, List<String> legacyHeaders, int maxRows, String rowLabel) {
        String filename = file.getOriginalFilename();
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024 || filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new IllegalArgumentException("Select a filled .xlsx template, 5 MB or smaller.");
        try (var input = file.getInputStream(); var workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0)
                throw new IllegalArgumentException("The workbook has no sheet.");
            var sheet = workbook.getSheetAt(0);
            var formatter = new DataFormatter(Locale.ROOT);
            var header = sheet.getRow(0);
            if (!matches(header, headers, formatter)) {
                if (legacyHeaders.isEmpty() || !matches(header, legacyHeaders, formatter))
                    throw new IllegalArgumentException("Use the downloaded template and keep its headers in order.");
                headers = legacyHeaders;
            }
            var result = new ArrayList<WorkbookRow>();
            for (var row : sheet) {
                if (row.getRowNum() == 0) continue;
                var values = new ArrayList<String>(headers.size());
                for (int i = 0; i < headers.size(); i++) values.add(value(row, i, formatter, headers.get(i)));
                if (values.stream().allMatch(String::isBlank)) continue;
                if (result.size() == maxRows) throw new IllegalArgumentException("Upload at most " + maxRows + " rows at a time.");
                result.add(new WorkbookRow(row.getRowNum() + 1, List.copyOf(values)));
            }
            if (result.isEmpty()) throw new IllegalArgumentException("Enter at least one " + rowLabel + " in the template.");
            return result;
        } catch (IOException | org.apache.poi.ooxml.POIXMLException e) {
            throw new IllegalArgumentException("Could not read the workbook. Open it in Excel and save it as .xlsx.", e);
        }
    }

    private static boolean matches(Row header, List<String> headers, DataFormatter formatter) {
        if (header == null) return false;
        for (int i = 0; i < headers.size(); i++) {
            if (!headers.get(i).equalsIgnoreCase(value(header, i, formatter, headers.get(i)))) return false;
        }
        return true;
    }

    private static String value(Row row, int column, DataFormatter formatter, String header) {
        var cell = row.getCell(column);
        if (cell == null) return "";
        if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR)
            throw new IllegalArgumentException("Row " + (row.getRowNum() + 1) + ": enter plain values in " + header + ".");
        return formatter.formatCellValue(cell).strip();
    }
}
