package com.vigilante.app;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
public class PageExplorerTest {
 @Test public void findsPriceStatusHeadlineAndLinks() {
  Document d=Jsoup.parse("<html><head><title>Shop</title><meta property='product:price:amount' content='99.50'></head><body><h1>Product</h1><p id='price'>Precio $99.50</p><div id='stock'>En stock</div><a href='/details'>Detalles</a></body></html>");
  List<PageExplorer.Candidate> c=PageExplorer.discover(d);
  assertTrue(c.stream().anyMatch(x->x.category.equals("Valor numérico")&&x.value.equals("99.50")));
  assertTrue(c.stream().anyMatch(x->x.category.equals("Disponibilidad / estado")&&x.value.equals("En stock")));
  assertTrue(c.stream().anyMatch(x->x.category.equals("Título")&&x.value.equals("Product")));
  assertTrue(c.stream().anyMatch(x->x.attribute.equals("href")&&x.value.equals("/details")));
  for(PageExplorer.Candidate x:c) assertEquals(1,d.select(x.selector).size());
 }
 @Test public void detectsExchangeRate() {
  Document d=Jsoup.parse("<p id='usd'>USD 420</p>");
  assertTrue(PageExplorer.discover(d).stream().anyMatch(x->x.category.equals("Valor numérico")&&x.value.equals("420")));
 }
}