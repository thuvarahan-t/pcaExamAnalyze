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

    static byte[] template() throws IOException {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Confirmed Purchases");
            var header = sheet.createRow(0);
            var text = workbook.createCellStyle();
            text.setDataFormat(workbook.createDataFormat().getFormat("@"));
            for (int i = 0; i < HEADERS.size(); i++) {
                header.createCell(i).setCellValue(HEADERS.get(i));
                sheet.setColumnWidth(i, (i == 3 ? 42 : 22) * 256);
                sheet.setDefaultColumnStyle(i, text);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    static List<BulkPurchase> read(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (file.isEmpty() || filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new IllegalArgumentException("Select a filled .xlsx template.");
        try (var input = file.getInputStream(); var workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0)
                throw new IllegalArgumentException("The workbook has no sheet.");
            var sheet = workbook.getSheetAt(0);
            var formatter = new DataFormatter(Locale.ROOT);
            var header = sheet.getRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                if (header == null || !HEADERS.get(i).equalsIgnoreCase(value(header, i, formatter)))
                    throw new IllegalArgumentException("Use the downloaded template and keep its headers in order.");
            }
            var result = new ArrayList<BulkPurchase>();
            for (var row : sheet) {
                if (row.getRowNum() == 0) continue;
                var values = new ArrayList<String>(HEADERS.size());
                for (int i = 0; i < HEADERS.size(); i++) values.add(value(row, i, formatter));
                if (values.stream().allMatch(String::isBlank)) continue;
                result.add(new BulkPurchase(row.getRowNum() + 1, values.get(0), values.get(1),
                    values.get(2), values.get(3), values.get(4)));
            }
            if (result.isEmpty()) throw new IllegalArgumentException("Enter at least one student in the template.");
            return result;
        } catch (IOException | org.apache.poi.ooxml.POIXMLException e) {
            throw new IllegalArgumentException("Could not read the workbook. Open it in Excel and save it as .xlsx.", e);
        }
    }

    private static String value(Row row, int column, DataFormatter formatter) {
        var cell = row.getCell(column);
        if (cell == null) return "";
        if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR)
            throw new IllegalArgumentException("Row " + (row.getRowNum() + 1) + ": enter plain values in " + HEADERS.get(column) + ".");
        return formatter.formatCellValue(cell).strip();
    }
}
