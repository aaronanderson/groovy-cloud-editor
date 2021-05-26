package com.github.aaronanderson.gce;

public class AutoCompleteRequest {
    private final int line;
    private final int ch;
    private final String sticky;

    private String constructorHint = null;
    private String propertyHint = null;

    public AutoCompleteRequest(int line, int ch, String sticky) {
        this.line = line;
        this.ch = ch;
        this.sticky = sticky;
    }

    public int getLine() {
        return line;
    }

    public int getCh() {
        return ch;
    }

    public String getSticky() {
        return sticky;
    }

    public String getConstructorHint() {
        return constructorHint;
    }

    public void setConstructorHint(String constructorHint) {
        this.constructorHint = constructorHint;
    }

    public String getPropertyHint() {
        return propertyHint;
    }

    public void setPropertyHint(String propertyHint) {
        this.propertyHint = propertyHint;
    }

}
