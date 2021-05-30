package com.github.aaronanderson.gce;

public class Hint {
    private final String type;
    final private int[] entered;
    final private String displayed;
    final private String value;
    final private String importValue;
    final private String importLine;

    public Hint(String type, int[] enteredText, String displayText, String value, String importValue, String importLine) {
        this.type = type;
        this.entered = enteredText;
        this.displayed = displayText;
        this.value = value;
        this.importValue = importValue;
        this.importLine = importLine;
    }

    public Hint(String type, int[] entered, String displayed, String value) {
        this.type = type;
        this.entered = entered;
        this.displayed = displayed;
        this.value = value;
        this.importValue = null;
        this.importLine = null;
    }
        

    public String getType() {
        return type;
    }

    public int[] getEntered() {
        return entered;
    }

    public String getDisplayed() {
        return displayed;
    }

    public String getValue() {
        return value;
    }

    public String getImportValue() {
        return importValue;
    }

    public String getImportLine() {
        return importLine;
    }

}