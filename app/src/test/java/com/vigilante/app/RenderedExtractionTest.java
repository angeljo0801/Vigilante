package com.vigilante.app;
import org.jsoup.Jsoup;
import org.junit.Test;
import static org.junit.Assert.*;
public class RenderedExtractionTest {
 @Test public void renderedDomUsesSameSelectorAndRule() throws Exception {
  Watch w=new Watch(); w.selector="#price"; w.regex="([0-9]+)"; w.keyword="100"; w.rule="BELOW";
  String text=WebScanner.extract(Jsoup.parse("<span id='price'>$79</span>"),w);
  WebScanner.Observation o=WebScanner.observe(w,text);
  assertEquals("79",o.observed); assertTrue(o.matched);
 }
 @Test public void duplicateValuesInDistinctElementsRemainSelectable(){
  assertTrue(PageExplorer.discover(Jsoup.parse("<p id='a'>Precio $5</p><p id='b'>Precio $5</p>")).stream().anyMatch(c->c.selector.equals("#a")));
  assertTrue(PageExplorer.discover(Jsoup.parse("<p id='a'>Precio $5</p><p id='b'>Precio $5</p>")).stream().anyMatch(c->c.selector.equals("#b")));
 }
}