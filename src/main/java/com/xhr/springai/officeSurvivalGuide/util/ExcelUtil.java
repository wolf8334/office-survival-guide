package com.xhr.springai.officeSurvivalGuide.util;

import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ExcelUtil {

    /**
     * 获取所有 Sheet 名称
     */
    public static List<String> getSheetNames(String filePath) throws Exception {
        List<String> sheetNames = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new File(filePath))) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheetNames.add(workbook.getSheetName(i));
            }
        }
        return sheetNames;
    }

    /**
     * 读取指定 Sheet 的特定列内容
     *
     * @param filePath            文件路径
     * @param sheetName           sheet页名称
     * @param hasHeader           是否跳过第一行标题行
     * @param targetColumnIndices 需要读取的列索引集合（例如：0代表第一列，2代表第三列）
     */
    public static List<List<String>> getSheetData(String filePath, String sheetName, boolean hasHeader, List<Integer> targetColumnIndices) throws Exception {
        List<List<String>> data = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(new File(filePath))) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) return data;

            int startRow = hasHeader ? 1 : 0;

            for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                List<String> rowData = new ArrayList<>();

                // 如果没有指定列，默认读取所有列
                if (targetColumnIndices == null || targetColumnIndices.isEmpty()) {
                    for (int j = 0; j < row.getLastCellNum(); j++) {
                        rowData.add(getCellValue(row.getCell(j)));
                    }
                } else {
                    // 只读取指定的列
                    for (Integer colIdx : targetColumnIndices) {
                        // 防止索引越界
                        if (colIdx >= 0 && colIdx < row.getLastCellNum()) {
                            rowData.add(getCellValue(row.getCell(colIdx)));
                        } else {
                            rowData.add(""); // 索引超出范围填空
                        }
                    }
                }
                data.add(rowData);
            }
        }
        return data;
    }

    private static String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) return cell.getDateCellValue().toString();
                // 简单处理数字，避免 1 变成 1.0
                DataFormatter formatter = new DataFormatter();
                return formatter.formatCellValue(cell);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }
}