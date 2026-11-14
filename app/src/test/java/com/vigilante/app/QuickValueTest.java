package com.vigilante.app;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
public class QuickValueTest {
 @Test public void selectionPrefersLatestUsdCupAndHonorsExplicitChoice(){
   Watch old=new Watch();old.name="USD/CUP anterior";
   Watch chosen=new Watch();chosen.name="USD/CUP corregido";
   Watch other=new Watch();other.name="Precio";
   NumericTrend.record(other,"20",1);
   assertSame(chosen,QuickValue.choose(Arrays.asList(old,chosen,other),""));
   assertSame(other,QuickValue.choose(Arrays.asList(old,chosen,other),other.id));
   assertNull(QuickValue.choose(Arrays.asList(other),"missing"));
 }
 @Test public void valueAndTrendReflectStoredReading(){
   Watch w=new Watch();w.name="USD/CUP";
   NumericTrend.record(w,"900",1);NumericTrend.record(w,"915",2);
   assertEquals("915 CUP ↑",QuickValue.label(w));
   assertEquals("↑ Subió +15 CUP",QuickValue.subtitle(w));
   w.lastError="Network";assertEquals("Dato sin actualizar",QuickValue.subtitle(w));
 }
}