package com.vigilante.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class InboxTaskTest{
 @Test public void validSpecificTaskCanActivate(){
  String raw="VIGILANTE/1\nURL: https://example.com\nCSS: #price\nREGLA: CHANGES\nHORAS: 08:00,12:00,20:00\nFIN";
  assertTrue(TaskImport.readyForAuto(TaskImport.parse(raw),raw));
 }
 @Test public void vagueTaskIsDraft(){
  String raw="VIGILANTE/1\nURL: https://example.com\nOBJETIVO: Encontrar precio\nHORAS: 08:00\nFIN";
  assertFalse(TaskImport.readyForAuto(TaskImport.parse(raw),raw));
 }
 @Test public void missingScheduleIsDraft(){
  String raw="VIGILANTE/1\nURL: https://example.com\nCSS: #price\nFIN";
  assertFalse(TaskImport.readyForAuto(TaskImport.parse(raw),raw));
 }
}