package com.vigilante.app;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.text.*;
import org.json.JSONObject;
import java.io.*;

final class ProposalPdf {
 private static final int W=612,H=792,L=48,R=564,BOTTOM=730,NAVY=0xff142a43,BLUE=0xff167b80,INK=0xff24384a,MUTED=0xff586d80;
 private final PdfDocument doc=new PdfDocument();private PdfDocument.Page page;private Canvas canvas;private int y,pageNo;private final boolean en,approved;
 private ProposalPdf(boolean en,boolean approved){this.en=en;this.approved=approved;}
 static byte[] create(JSONObject j,boolean approved)throws IOException{ProposalPdf p=new ProposalPdf("EN".equals(j.optString("language")),approved);try{p.start();p.text(j.optString("title"),25,NAVY,true);p.text(j.optString("subtitle"),14,BLUE,false);p.section(p.en?"Prepared for":"Preparado para",j.optString("recipient"));String[] keys={"problem","solution","benefits","deliverables","pricing","impact","nextSteps","sources","contact"};String[] es={"Necesidad u oportunidad","Nuestra solución","Beneficios para el cliente","Qué incluye","Precio y condiciones","Impacto estimado y supuestos","Próximo paso","Fuentes públicas","Contacto"};String[] eng={"Need or opportunity","Our solution","Customer benefits","What is included","Pricing and terms","Estimated impact and assumptions","Next step","Public sources","Contact"};for(int i=0;i<keys.length;i++)p.section((p.en?eng:es)[i],j.optString(keys[i]));p.finish();ByteArrayOutputStream out=new ByteArrayOutputStream();p.doc.writeTo(out);return out.toByteArray();}finally{p.doc.close();}}
 private void start(){page=doc.startPage(new PdfDocument.PageInfo.Builder(W,H,++pageNo).create());canvas=page.getCanvas();Paint a=new Paint(Paint.ANTI_ALIAS_FLAG);a.setColor(NAVY);canvas.drawRect(0,0,W,72,a);a.setColor(Color.WHITE);a.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));a.setTextSize(12);canvas.drawText(en?"BUSINESS PROPOSAL":"PROPUESTA COMERCIAL",L,28,a);a.setTypeface(Typeface.DEFAULT);a.setTextSize(10);canvas.drawText(approved?(en?"For discussion":"Para evaluación"):(en?"DISCOVERY DRAFT · NOT AN APPROVED OFFER":"BORRADOR DE VALIDACIÓN · NO ES UNA OFERTA APROBADA"),L,49,a);y=96;}
 private void finish(){Paint a=new Paint(Paint.ANTI_ALIAS_FLAG);a.setColor(0xffc6d3df);canvas.drawLine(L,750,R,750,a);a.setColor(MUTED);a.setTextSize(10);canvas.drawText((en?"Commercial presentation":"Presentación comercial")+"  •  "+pageNo,L,768,a);doc.finishPage(page);page=null;}
 private void ensure(int h){if(y+h>BOTTOM){finish();start();}}
 private void section(String title,String body){if(body==null||body.trim().isEmpty())return;ensure(66);y+=8;text(title,14,BLUE,true);text(body,12,INK,false);y+=8;}
 private void text(String s,int size,int color,boolean bold){if(s==null||s.trim().isEmpty())return;TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setTextSize(size);paint.setColor(color);paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));StaticLayout layout=StaticLayout.Builder.obtain(s,0,s.length(),paint,R-L).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(3,1).build();for(int i=0;i<layout.getLineCount();i++){int top=layout.getLineTop(i),bottom=layout.getLineBottom(i),h=Math.max(1,bottom-top);ensure(h);canvas.save();canvas.clipRect(L,y,R,y+h);canvas.translate(L,y-top);layout.draw(canvas);canvas.restore();y+=h;}y+=5;}
}
