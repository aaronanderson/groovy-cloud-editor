package com.github.aaronanderson.gce;

public class Hint {
    final private int[] entered;
    final private String displayed;
    final private String value;
    final private String importValue;
    final private String importLine;

    public Hint(int[] enteredText, String displayText, String value, String importValue, String importLine) {
        this.entered = enteredText;
        this.displayed = displayText;
        this.value = value;
        this.importValue = importValue;
        this.importLine = importLine;
    }

    public Hint(int[] entered, String displayed, String value) {
        this.entered = entered;
        this.displayed = displayed;
        this.value = value;
        this.importValue = null;
        this.importLine = null;
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