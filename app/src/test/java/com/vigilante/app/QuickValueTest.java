package com.vigilante.app;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
public class QuickValueTest {
 @Test public void automaticSelectsLatestReadingAndExplicitChoiceWorks(){
   Watch price=new Watch();price.name="USD/CUP";price.initialized=true;price.lastChecked=100;price.lastPreview="900";
   Watch status=new Watch();status.name="Pedido";status.initialized=true;status.lastChecked=200;status.lastPreview="Entregado";
   assertSame(status,QuickValue.choose(Arrays.asList(price,status),""));
   assertSame(price,QuickValue.choose(Arrays.asList(price,status),price.id));
   assertNull(QuickValue.choose(Arrays.asList(status),"missing"));
 }
 @Test public void numericAndTextTrend(){
   Watch w=new Watch();w.name="USD/CUP";w.initialized=true;
   NumericTrend.record(w,"900",1);NumericTrend.record(w,"915",2);
   assertEquals("915 CUP ↑",QuickValue.label(w));assertEquals("↑ Subió +15 CUP",QuickValue.subtitle(w));
   w.lastError="Network";assertEquals("Dato sin actualizar",QuickValue.subtitle(w));assertEquals("915 CUP !",QuickValue.label(w));
   Watch status=new Watch();status.initialized=true;status.lastPreview="Entregado";status.previousValueKnown=true;status.previousValueChanged=true;
   assertEquals("Entregado ↻",QuickValue.label(status));assertEquals("Cambió",QuickValue.subtitle(status));
   assertEquals("Entregado",QuickValue.iconText(status));
 }
 @Test public void longTextIsAbbreviatedAndNoReadingDoesNotInventValue(){
   Watch w=new Watch();w.lastPreview="Entrega prevista para el próximo lunes";
   assertEquals("Sin lectura",QuickValue.label(w));w.initialized=true;
   assertTrue(QuickValue.iconText(w).endsWith("…"));
   assertTrue(QuickValue.iconText(w).codePointCount(0,QuickValue.iconText(w).length())<=12);
 }
}