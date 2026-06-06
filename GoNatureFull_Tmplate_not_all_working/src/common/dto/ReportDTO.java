package common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ReportDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String title;
    private List<String> rows = new ArrayList<>();

    public ReportDTO(String title) { this.title = title; }
    public String getTitle() { return title; }
    public List<String> getRows() { return rows; }
    public void addRow(String row) { rows.add(row); }
    public String toString() { return title + "\n" + String.join("\n", rows); }
}
