package org.example;

import java.nio.file.Files;
import java.nio.file.Paths;

public class LargeString {
    String ExampleString;

    public String getRegexPattern() {
        return RegexPattern;
    }

    public void setRegexPattern(String regexPattern) {
        RegexPattern = regexPattern;
    }

    String RegexPattern;
    public String getExampleString() {
        return ExampleString;
    }
    public void setExampleString(String exampleString) {
        ExampleString = exampleString;
    }
    public LargeString(){
        RegexPattern = "\\bhttps?://\\S+\\b";
        try{
            FileReadEvent event  = new FileReadEvent();
            event.begin();
            ExampleString = new String(Files.readAllBytes(Paths.get("/Users/harsh.kumar/Downloads/large-file.json")));
            if(ExampleString.length()>0){
                event.Indicator = true;
                event.end();
                event.commit();
            }
            else {
                event.Indicator = false;
                event.commit();
            }
        }
        catch(Exception e){
            return;
        }
    }


}
