package mg.itu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class ExcelMappingConfig {
    
    @JsonProperty("nomTable")
    private String nomTable;
    
    @JsonProperty("typeMap")
    private Map<String, String> typeMap;
    
    @JsonProperty("map")
    private List<MappingSection> map;
    
    public ExcelMappingConfig() {}
    
    public ExcelMappingConfig(String nomTable, Map<String, String> typeMap, List<MappingSection> map) {
        this.nomTable = nomTable;
        this.typeMap = typeMap;
        this.map = map;
    }
    
    
    public String getNomTable() {
        return nomTable;
    }
    
    public void setNomTable(String nomTable) {
        this.nomTable = nomTable;
    }
    
    public Map<String, String> getTypeMap() {
        return typeMap;
    }
    
    public void setTypeMap(Map<String, String> typeMap) {
        this.typeMap = typeMap;
    }
    
    public List<MappingSection> getMap() {
        return map;
    }
    
    public void setMap(List<MappingSection> map) {
        this.map = map;
    }
    
    
    public String getFieldSqlType(String fieldName) {
        if (typeMap != null && typeMap.containsKey(fieldName)) {
            return typeMap.get(fieldName);
        }
        return "VARCHAR2(255)"; 
    }
    
    public static class MappingSection {
        
        @JsonProperty("debutdata")
        private Integer debutdata;
        
        @JsonProperty("findata")
        private Integer findata;
        
        @JsonProperty("columnMapping")
        private Map<String, String> columnMapping;
        
        public MappingSection() {}
        
        public MappingSection(Integer debutdata, Integer findata, Map<String, String> columnMapping) {
            this.debutdata = debutdata;
            this.findata = findata;
            this.columnMapping = columnMapping;
        }
        
        
        public Integer getDebutdata() {
            return debutdata;
        }
        
        public void setDebutdata(Integer debutdata) {
            this.debutdata = debutdata;
        }
        
        public Integer getFindata() {
            return findata;
        }
        
        public void setFindata(Integer findata) {
            this.findata = findata;
        }
        
        public Map<String, String> getColumnMapping() {
            return columnMapping;
        }
        
        public void setColumnMapping(Map<String, String> columnMapping) {
            this.columnMapping = columnMapping;
        }
        
        
        public String getFieldName(int columnIndex) {
            return columnMapping != null ? columnMapping.get(String.valueOf(columnIndex)) : null;
        }
        
        public boolean hasColumnMapping(int columnIndex) {
            return columnMapping != null && columnMapping.containsKey(String.valueOf(columnIndex));
        }
    }
    
    @Override
    public String toString() {
        return "ExcelMappingConfig{" +
                "nomTable='" + nomTable + '\'' +
                ", typeMap=" + typeMap +
                ", map=" + map +
                '}';
    }
}