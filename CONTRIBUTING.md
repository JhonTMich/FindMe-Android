# Contribuir a Near

Abre los pull requests de funcionalidades, correcciones normales y dependencias hacia `develop`. GitHub comprueba la compilación, el lint y las pruebas de Android antes de permitir la integración.

## Cambios en main

- **Lanzamientos.** Abre un pull request de `develop` a `main` y fusiónalo con un commit de merge para conservar el historial compartido. Luego crea la etiqueta de versión sobre el commit de `main` que se va a publicar.
- **Documentación.** Puedes abrir un pull request directo a `main` para editar los archivos Markdown README, CHANGELOG, CONTRIBUTING o SECURITY de la raíz, o texto e imágenes de documentación dentro de `docs/`.
- **Hotfixes.** Crea una rama `hotfix/<nombre>` desde `main` en este repositorio y abre un pull request hacia `main`. Mantén el cambio acotado a la corrección urgente.

Después de integrar documentación o un hotfix directamente en `main`, abre un pull request de `main` a `develop` y usa un commit de merge para incorporar esos cambios antes del siguiente lanzamiento.