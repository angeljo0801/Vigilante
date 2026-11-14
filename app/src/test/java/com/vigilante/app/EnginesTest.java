package com.vigilante.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
public class EnginesTest {
 @Test public void chainRespectsOriginAndConfiguredBridge(){
  assertEquals(Arrays.asList("HTML","WEBVIEW","PLAYWRIGHT"),Engines.chain("",true));
  assertEquals(Arrays.asList("WEBVIEW"),Engines.chain("WEBVIEW",false));
  assertEquals(Arrays.asList("PLAYWRIGHT"),Engines.chain("PLAYWRIGHT",true));
  assertTrue(Engines.chain("PLAYWRIGHT",false).isEmpty());
 }
}