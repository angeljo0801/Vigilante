package com.vigilante.app;
final class BusinessAgents {
 static final String[][] ALL = {{"Mercado","Investigador de mercado","Demanda, tamaño, búsquedas, tendencias, estacionalidad, regiones y sustitutos"},
{"Mercado","Investigador de competencia","Competidores, precios, anuncios, ofertas, posicionamiento y debilidades"},
{"Mercado","Detector de problemas","Reseñas, quejas, necesidades insatisfechas y evidencia verificable"},
{"Mercado","Investigador de clientes","Segmentos, compradores, hábitos, capacidad de pago y canales"},
{"Mercado","Investigador de tendencias","Productos, servicios e innovaciones emergentes; oportunidades y fuentes"},
{"Producto y finanzas","Diseñador de soluciones","Especificaciones, diferenciación, MVP y validación"},
{"Producto y finanzas","Investigador de proveedores","Fabricantes, cotizaciones, MOQ, muestras, logística y calidad"},
{"Producto y finanzas","Analista financiero","Costos, margen, CAC, escenarios, capital y punto de equilibrio"},
{"Producto y finanzas","Especialista en ofertas","Posicionamiento, propuesta de valor, precios, garantías y objeciones"},
{"Marketing","Estratega de marketing","Canales, audiencias, objetivos, presupuesto y lanzamiento"},
{"Marketing","Diseñador creativo","Identidad, conceptos visuales, brief de imágenes y anuncios"},
{"Marketing","Creador audiovisual","Guiones, reels, demostraciones y plan de producción"},
{"Marketing","Redactor publicitario","Textos, títulos, CTA, landing y mensajes comerciales"},
{"Marketing","Community manager","Calendario, publicación propuesta, respuestas y comunidades"},
{"Marketing","Publicidad pagada","Campañas, audiencias, pruebas, presupuesto y límites"},
{"Ventas","Buscador de clientes","Prospectos concretos, contactos públicos, criterios y evidencia"},
{"Ventas","Especialista en ventas","Propuestas, negociación, demostraciones y seguimiento"},
{"Ventas","Comercio electrónico","Canales, tienda, fichas, checkout y conversiones"},
{"Ventas","Especialista en alianzas","Influencers, afiliados, distribuidores, socios y condiciones"},
{"Crecimiento","Analista de datos","KPIs, instrumentación, CAC, conversiones y rentabilidad"},
{"Crecimiento","Experimentación","Hipótesis, A/B, tamaños de muestra y reglas de decisión"},
{"Crecimiento","Estratega de crecimiento","Escalado, mercados, nuevos canales y riesgos"},
{"Operaciones","Gestor de operaciones","Inventario, entregas, capacidad, procesos y costos"},
{"Operaciones","Atención al cliente","Preguntas, soporte, reclamaciones y tiempos"},
{"Operaciones","Fidelización","Recompras, referidos y retención"},
{"Operaciones","Riesgos y cumplimiento","Reglas, seguridad, propiedad intelectual y riesgos por mercado"},
{"Dirección","Director general","Integra informes, contradicciones, decisión justificada y próximos pasos"}};
 static final String[] DEPTS={"Mercado","Producto y finanzas","Marketing","Ventas","Crecimiento","Operaciones","Dirección"};
 static boolean pre(int id){return id<=9||id==26||id==27;}
 private BusinessAgents(){}
}
