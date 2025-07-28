package mg.itu.service;

import mg.itu.model.ExcelMappingConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument;
import org.odftoolkit.odfdom.doc.table.OdfTable;
import org.odftoolkit.odfdom.doc.table.OdfTableCell;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExcelProcessorService {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    

    public String generateSqlScript(MultipartFile file, String jsonConfig) throws IOException, CsvException {
        ExcelMappingConfig config = parseJsonConfig(jsonConfig);
        List<List<String>> fileData = readFile(file);
        return generateInsertScript(fileData, config);
    }
    
    public ExcelMappingConfig parseJsonConfig(String jsonConfig) throws JsonProcessingException {
        return objectMapper.readValue(jsonConfig, ExcelMappingConfig.class);
    }
    
    private List<List<String>> readFile(MultipartFile file) throws IOException, CsvException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IOException("Nom de fichier non disponible");
        }
        
        try (InputStream inputStream = file.getInputStream()) {
            if (fileName.endsWith(".xlsx")) {
                return readExcelFile(new XSSFWorkbook(inputStream));
            } else if (fileName.endsWith(".xls")) {
                return readExcelFile(new HSSFWorkbook(inputStream));
            } else if (fileName.endsWith(".csv")) {
                return readCsvFile(inputStream);
            } else if (fileName.endsWith(".ods")) {
                return readOdsFile(inputStream);
            } else {
                throw new IOException("Type de fichier non supporté: " + fileName);
            }
        }
    }
    
    private List<List<String>> readExcelFile(Workbook workbook) throws IOException {
        List<List<String>> data = new ArrayList<>();
        Sheet sheet = workbook.getSheetAt(0);
        
        int maxColumns = 0;
        for (Row row : sheet) {
            if (row.getLastCellNum() > maxColumns) {
                maxColumns = row.getLastCellNum();
            }
        }
        
        for (Row row : sheet) {
            List<String> rowData = new ArrayList<>();
            for (int cellIndex = 0; cellIndex < maxColumns; cellIndex++) {
                Cell cell = row.getCell(cellIndex);
                rowData.add(getCellValueAsString(cell));
            }
            data.add(rowData);
        }
        
        workbook.close();
        return data;
    }
    
    private List<List<String>> readCsvFile(InputStream inputStream) throws IOException, CsvException {
        List<List<String>> data = new ArrayList<>();
        try (CSVReader csvReader = new CSVReader(new InputStreamReader(inputStream))) {
            List<String[]> allRows = csvReader.readAll();
            
            int maxColumns = 0;
            for (String[] csvRow : allRows) {
                if (csvRow.length > maxColumns) {
                    maxColumns = csvRow.length;
                }
            }
            
            for (String[] csvRow : allRows) {
                List<String> rowData = new ArrayList<>();
                for (int i = 0; i < maxColumns; i++) {
                    rowData.add(i < csvRow.length ? csvRow[i] : "");
                }
                data.add(rowData);
            }
        } catch (CsvValidationException e) {
            throw new IOException("Erreur lors de la lecture du fichier CSV: " + e.getMessage());
        }
        return data;
    }
    
    private List<List<String>> readOdsFile(InputStream inputStream) throws IOException {
        List<List<String>> data = new ArrayList<>();
        try (OdfSpreadsheetDocument odsDoc = OdfSpreadsheetDocument.loadDocument(inputStream)) {
            OdfTable table = odsDoc.getTableList().get(0);
            int rowCount = table.getRowCount();
            int maxColumns = table.getColumnCount();
            
            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                List<String> rowData = new ArrayList<>();
                for (int colIndex = 0; colIndex < maxColumns; colIndex++) {
                    OdfTableCell cell = table.getCellByPosition(colIndex, rowIndex);
                    String value = cell.getStringValue() != null ? cell.getStringValue() : "";
                    rowData.add(value);
                }
                data.add(rowData);
            }
        } catch (Exception e) {
            throw new IOException("Erreur lors de la lecture du fichier ODS: " + e.getMessage());
        }
        return data;
    }
    
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return dateFormat.format(cell.getDateCellValue());
                } else {
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == Math.floor(numericValue)) {
                        return String.valueOf((long) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cellValue = evaluator.evaluate(cell);
                    if (cellValue.getCellType() == CellType.NUMERIC) {
                        double numValue = cellValue.getNumberValue();
                        if (numValue == Math.floor(numValue)) {
                            return String.valueOf((long) numValue);
                        } else {
                            return String.valueOf(numValue);
                        }
                    } else if (cellValue.getCellType() == CellType.STRING) {
                        return cellValue.getStringValue().trim();
                    } else {
                        return cell.getCellFormula();
                    }
                } catch (Exception e) {
                    return cell.getCellFormula();
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }
    
    public Map<String, Object> getExcelFileInfo(MultipartFile file) throws Exception {
        Map<String, Object> info = new HashMap<>();
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IOException("Nom de fichier non disponible");
        }
        
        try (InputStream inputStream = file.getInputStream()) {
            info.put("fileName", fileName);
            info.put("fileSize", file.getSize());
            
            if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                Workbook workbook = fileName.endsWith(".xlsx") ? new XSSFWorkbook(inputStream) : new HSSFWorkbook(inputStream);
                Sheet sheet = workbook.getSheetAt(0);
                info.put("numberOfSheets", workbook.getNumberOfSheets());
                info.put("activeSheetName", sheet.getSheetName());
                info.put("totalRows", sheet.getLastRowNum() + 1);
                
                int maxColumns = 0;
                for (Row row : sheet) {
                    if (row.getLastCellNum() > maxColumns) {
                        maxColumns = row.getLastCellNum();
                    }
                }
                info.put("maxColumns", maxColumns);
                workbook.close();
            } else if (fileName.endsWith(".csv")) {
                try (CSVReader csvReader = new CSVReader(new InputStreamReader(inputStream))) {
                    List<String[]> allRows = csvReader.readAll();
                    info.put("numberOfSheets", 1);
                    info.put("activeSheetName", "Sheet1");
                    info.put("totalRows", allRows.size());
                    
                    int maxColumns = 0;
                    for (String[] csvRow : allRows) {
                        if (csvRow.length > maxColumns) {
                            maxColumns = csvRow.length;
                        }
                    }
                    info.put("maxColumns", maxColumns);
                } catch (CsvValidationException e) {
                    throw new IOException("Erreur lors de la lecture du fichier CSV: " + e.getMessage());
                }
            } else if (fileName.endsWith(".ods")) {
                try (OdfSpreadsheetDocument odsDoc = OdfSpreadsheetDocument.loadDocument(inputStream)) {
                    OdfTable table = odsDoc.getTableList().get(0);
                    info.put("numberOfSheets", odsDoc.getTableList().size());
                    info.put("activeSheetName", table.getTableName());
                    info.put("totalRows", table.getRowCount());
                    info.put("maxColumns", table.getColumnCount());
                }
            } else {
                throw new IOException("Type de fichier non supporté: " + fileName);
            }
        }
        
        return info;
    }
    
    private String generateInsertScript(List<List<String>> excelData, ExcelMappingConfig config) {
        List<String> allInsertClauses = new ArrayList<>();
        
        for (int sectionIndex = 0; sectionIndex < config.getMap().size(); sectionIndex++) {
            ExcelMappingConfig.MappingSection section = config.getMap().get(sectionIndex);
            
            int startRow = Math.max(0, section.getDebutdata() - 1);
            int endRow = section.getFindata() != null ? 
                        Math.min(section.getFindata() - 1, excelData.size() - 1) : 
                        excelData.size() - 1;
            
            List<String> insertClauses = generateInsertStatementsForSection(
                excelData, section, startRow, endRow, config
            );
            
            allInsertClauses.addAll(insertClauses);
        }
        
        if (allInsertClauses.isEmpty()) {
            return "";
        }
        
        StringBuilder script = new StringBuilder();
        script.append("INSERT ALL\n");
        for (String clause : allInsertClauses) {
            script.append("    ").append(clause).append("\n");
        }
        script.append("SELECT * FROM dual;");
        
        return script.toString();
    }
    
    private List<String> generateInsertStatementsForSection(
            List<List<String>> excelData, 
            ExcelMappingConfig.MappingSection section,
            int startRow, 
            int endRow, 
            ExcelMappingConfig config) {
        
        List<String> insertClauses = new ArrayList<>();
        
        Map<String, String> columnMapping = section.getColumnMapping();
        if (columnMapping == null || columnMapping.isEmpty()) {
            return insertClauses;
        }
        
        List<Map.Entry<String, String>> sortedColumns = columnMapping.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(Integer.parseInt(e1.getKey()), Integer.parseInt(e2.getKey())))
                .collect(Collectors.toList());
        
        List<String> columnNames = sortedColumns.stream()
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        if (columnNames.isEmpty()) {
            return insertClauses;
        }
        
        String columnList = String.join(", ", columnNames);
        String tableName = config.getNomTable();
        
        for (int rowIndex = startRow; rowIndex <= endRow && rowIndex < excelData.size(); rowIndex++) {
            List<String> rowData = excelData.get(rowIndex);
            
            boolean hasSignificantData = false;
            for (Map.Entry<String, String> entry : sortedColumns) {
                int colIndex = Integer.parseInt(entry.getKey()) - 1;
                if (colIndex < rowData.size()) {
                    String cellValue = rowData.get(colIndex);
                    if (cellValue != null && !cellValue.trim().isEmpty()) {
                        hasSignificantData = true;
                        break;
                    }
                }
            }
            
            if (!hasSignificantData) {
                continue;
            }
            
            StringBuilder insertClause = new StringBuilder();
            insertClause.append("INTO ").append(tableName).append(" (").append(columnList).append(") VALUES (");
            
            List<String> values = new ArrayList<>();
            for (Map.Entry<String, String> entry : sortedColumns) {
                int colIndex = Integer.parseInt(entry.getKey()) - 1;
                String fieldName = entry.getValue();
                
                if (colIndex < rowData.size()) {
                    String cellValue = rowData.get(colIndex);
                    values.add(formatSqlValue(cellValue, fieldName, config));
                } else {
                    values.add("NULL");
                }
            }
            
            insertClause.append(String.join(", ", values));
            insertClause.append(")");
            
            insertClauses.add(insertClause.toString());
        }
        
        return insertClauses;
    }
    
    private String formatSqlValue(String value, String fieldName, ExcelMappingConfig config) {
        if (value == null || value.trim().isEmpty()) {
            return "NULL";
        }
        
        value = value.trim();
        
        String sqlType = config.getFieldSqlType(fieldName);
        
        if (sqlType.toUpperCase().startsWith("NUMBER") || sqlType.toUpperCase().startsWith("INTEGER")) {
            try {
                if (value.contains(".")) {
                    Double.parseDouble(value);
                    return value;
                } else {
                    Long.parseLong(value);
                    return value;
                }
            } catch (NumberFormatException e) {
                return "NULL";
            }
        }
        
        if (sqlType.toUpperCase().startsWith("DATE")) {
            try {
                if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    return "TO_DATE('" + value + "', 'YYYY-MM-DD')";
                } else if (value.matches("\\d{2}/\\d{2}/\\d{4}")) {
                    return "TO_DATE('" + value + "', 'DD/MM/YYYY')";
                } else if (value.matches("\\d{2}-\\d{2}-\\d{4}")) {
                    return "TO_DATE('" + value + "', 'DD-MM-YYYY')";
                } else if (value.matches("\\d{4}/\\d{2}/\\d{2}")) {
                    return "TO_DATE('" + value + "', 'YYYY/MM/DD')";
                } else {
                    return "'" + value.replace("'", "''") + "'";
                }
            } catch (Exception e) {
                return "'" + value.replace("'", "''") + "'";
            }
        }
        
        return "'" + value.replace("'", "''") + "'";
    }

    
}