# Mando 0.4.1

Ajuste Mostrar pestaña IR persistente por móvil, booleano validado en el puente nativo. Valor inicial según disponibilidad del emisor; pestaña oculta hasta cargar la preferencia.

33 comprobaciones de interfaz sin overflow ni errores JS, más 8 comprobaciones del ajuste: valores predeterminados con/sin hardware, cambio de visibilidad, recuperación tras recarga y restauración. En la prueba web se simulan hardware y preferencias; persistencia Android implementada con SharedPreferences privado. Compilación y firma v3 verificadas.

El usuario confirmó previamente que los cinco botones LGTV1 de 0.4.0 funcionan físicamente en su televisor. No se modifica ninguna señal IR.

El usuario confirmó después que todo funciona y declaró 0.4.1 versión estable. Esto incluye la preferencia para mostrar/ocultar IR.
