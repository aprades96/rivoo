# Docker para Mortales — Aplicado a Rivoo

## El problema que Docker resuelve

Sin Docker:

```
Tu maquina                    Servidor de produccion
├── Java 25                   ├── Java 21 (version distinta!)
├── MySQL 8.0.43              ├── MySQL 8.0.35
├── Maven 3.9.11              ├── Maven no instalado
├── Windows 11                ├── Ubuntu 22.04
└── "en mi maquina funciona"  └── "en produccion no funciona"
```

Con Docker:

```
Tu maquina                    Servidor de produccion
└── Docker                    └── Docker
    └── misma imagen              └── misma imagen
    └── mismo resultado           └── mismo resultado
```

Docker empaqueta tu aplicacion + todo lo que necesita (Java, librerias, config) en una "caja" que funciona igual en cualquier sitio.

---

## Los 4 conceptos clave

### 1. Imagen = receta de cocina

Una imagen es un archivo que dice "como construir una caja". Es como una receta:
- Empieza con Ubuntu
- Instala Java 25
- Copia mi JAR
- Cuando arranques, ejecuta `java -jar app.jar`

Las imagenes se crean con un **Dockerfile** (el archivo de receta) y se guardan en un **registry** (Docker Hub, como un supermercado de recetas).

```
Dockerfile  --[docker build]-->  Imagen  --[docker run]-->  Contenedor
(receta)                         (plato preparado)          (plato servido)
```

### 2. Contenedor = la caja corriendo

Un contenedor es una imagen en ejecucion. Es como una maquina virtual pero mucho mas ligera:

| Maquina virtual | Contenedor |
|-----------------|------------|
| Incluye un SO completo (Windows/Linux) | Comparte el SO del host |
| 2-10 GB de disco | 50-500 MB |
| Tarda minutos en arrancar | Tarda segundos |
| Pesada | Ligera |

Puedes tener 10 contenedores en una maquina que no aguantaria 3 VMs.

### 3. Dockerfile = las instrucciones

Un Dockerfile es un archivo de texto con instrucciones paso a paso. Veamos el de `salon-service`:

```dockerfile
# PASO 1: Empezar con una imagen que ya tiene Java 25 (JDK = compilador)
FROM eclipse-temurin:25-jdk AS builder

# PASO 2: Crear una carpeta de trabajo
WORKDIR /build

# PASO 3: Copiar los archivos del proyecto
COPY pom.xml .
COPY rivoo-common/pom.xml rivoo-common/pom.xml
COPY salon-service/pom.xml salon-service/pom.xml
# ... (todos los POMs)

# PASO 4: Descargar dependencias Maven
RUN mvn dependency:go-offline

# PASO 5: Copiar todo el codigo fuente
COPY . .

# PASO 6: Compilar (crear el JAR)
RUN mvn clean package -DskipTests -pl rivoo-common,salon-service -am

# === AQUI EMPIEZA UNA SEGUNDA IMAGEN (mas pequena) ===

# PASO 7: Empezar con una imagen que solo tiene Java 25 (JRE = solo ejecucion)
FROM eclipse-temurin:25-jre

# PASO 8: Copiar SOLO el JAR de la imagen anterior
COPY --from=builder /build/salon-service/target/*.jar app.jar

# PASO 9: Decir que puerto usa
EXPOSE 8082

# PASO 10: Comando para arrancar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Esto se llama **multi-stage build**: la primera etapa compila (imagen grande con JDK + Maven + codigo fuente), la segunda solo tiene el JAR final (imagen pequena con JRE).

```
Stage 1 (builder): ~800 MB  →  compila todo
Stage 2 (runtime): ~200 MB  →  solo el JAR + JRE  ← esta es la imagen final
```

### 4. Docker Compose = orquestador local

Un `docker-compose.yml` es un archivo que dice "arranca estas 5 cajas juntas y conectalas entre si".

Veamos una version simplificada del de Rivoo:

```yaml
services:
  # Caja 1: MySQL
  mysql:
    image: mysql:8.0          # Usa la receta oficial de MySQL
    environment:
      MYSQL_ROOT_PASSWORD: root
    ports:
      - "3306:3306"            # Tu maquina:3306 → contenedor:3306

  # Caja 2: Keycloak
  keycloak:
    image: quay.io/keycloak/keycloak:26.0.6
    ports:
      - "9080:9080"
    depends_on:
      - mysql                  # Espera a que MySQL arranque primero

  # Caja 3: salon-service
  salon-service:
    build: ./salon-service     # Construye la imagen desde el Dockerfile
    ports:
      - "8082:8082"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/salon_db
      #                                  ^^^^^ nombre del servicio, NO localhost!
    depends_on:
      - mysql
      - keycloak
```

Lo importante:
- `image: mysql:8.0` → usa una imagen ya hecha (del "supermercado" Docker Hub)
- `build: ./salon-service` → construye la imagen desde tu Dockerfile
- `mysql:3306` → dentro de Docker, los contenedores se llaman por nombre. No es localhost.
- `depends_on` → "no arranques hasta que este otro este listo"
- `ports: "8082:8082"` → "conecta el puerto 8082 de mi maquina al 8082 del contenedor"

---

## Docker aplicado a Rivoo

### Que tenemos

```
rivoo/
├── docker-compose.yml           ← orquesta todo para desarrollo local
├── .dockerignore                ← que NO copiar a las imagenes
├── Dockerfile.service           ← plantilla generica (referencia)
├── salon-service/
│   ├── Dockerfile               ← instrucciones para construir salon-service
│   ├── src/                     ← codigo fuente
│   └── pom.xml
├── auth-service/
│   ├── Dockerfile
│   ├── src/
│   └── pom.xml
└── ... (9 servicios, cada uno con su Dockerfile)
```

### Comandos basicos

```bash
# CONSTRUIR todas las imagenes
docker compose build
# Esto ejecuta cada Dockerfile: descarga Java, compila, crea las imagenes.
# Tarda ~5 min la primera vez. Despues, Docker CACHEA los pasos que no cambiaron.

# ARRANCAR todo
docker compose up
# Arranca MySQL → Keycloak → 9 servicios. Ves los logs de todos en la terminal.

# ARRANCAR en segundo plano (sin ocupar terminal)
docker compose up -d

# VER que esta corriendo
docker compose ps
# Muestra: nombre, estado, puertos

# VER logs de un servicio
docker compose logs salon-service
docker compose logs -f salon-service    # -f = follow (tiempo real)

# PARAR todo
docker compose down

# PARAR y BORRAR los datos (MySQL pierde todo)
docker compose down -v
# -v = eliminar volumes (los datos persistentes)

# RECONSTRUIR un servicio (despues de cambiar codigo)
docker compose build salon-service
docker compose up -d salon-service
```

### La magia del cache

Mira otra vez el Dockerfile:

```dockerfile
# Paso 1-3: Copiar POMs (solo cambian si tocas dependencias)
COPY pom.xml .
COPY salon-service/pom.xml salon-service/pom.xml

# Paso 4: Descargar dependencias (CACHEADO si los POMs no cambiaron)
RUN mvn dependency:go-offline

# Paso 5: Copiar codigo (cambia cada vez que editas)
COPY . .

# Paso 6: Compilar
RUN mvn clean package
```

Docker ejecuta cada paso y guarda el resultado. Si un paso no cambio, usa el cache:

```
Primer build:    POM → descarga deps (3 min) → copia codigo → compila (30s) = 3.5 min
Segundo build:   POM no cambio → CACHE → copia codigo → compila (30s) = 30s
```

Por eso copiamos los POMs ANTES que el codigo: las dependencias cambian poco, el codigo cambia mucho. Asi Docker reutiliza el cache de dependencias.

### Ports: la puerta al contenedor

Cada contenedor es una caja cerrada. Para acceder desde fuera, abres un "puerto":

```
Tu navegador → localhost:8082 → [PUERTA] → contenedor salon-service:8082 → Spring Boot
```

En docker-compose.yml:

```yaml
ports:
  - "8082:8082"    # host:container
```

Si pusieras `"9999:8082"`, accederias por `localhost:9999` pero dentro del contenedor sigue siendo 8082.

### Networking: como se hablan los contenedores

Dentro de Docker Compose, los contenedores se ven por nombre:

```
salon-service quiere hablar con MySQL:
  ✗ jdbc:mysql://localhost:3306/salon_db      ← MAL (localhost = el propio contenedor)
  ✓ jdbc:mysql://mysql:3306/salon_db          ← BIEN (mysql = nombre del servicio)

salon-service quiere hablar con auth-service:
  ✗ http://localhost:8081                     ← MAL
  ✓ http://auth-service:8081                  ← BIEN
```

Docker crea una red privada donde cada servicio tiene su nombre como "hostname".

### Volumes: datos que sobreviven

Por defecto, si paras un contenedor, sus datos desaparecen. Para que MySQL no pierda los datos:

```yaml
mysql:
  volumes:
    - mysql-data:/var/lib/mysql    # "guarda /var/lib/mysql en un sitio permanente"

volumes:
  mysql-data:                       # Docker gestiona donde lo guarda fisicamente
```

Asi puedes hacer `docker compose down` y `docker compose up` sin perder la BD.

---

## De local a Railway

Hasta ahora Docker es para tu maquina local. Pero las imagenes que construyes localmente son las mismas que Railway ejecuta en la nube.

```
LOCAL:
  docker compose build salon-service  →  crea imagen en tu maquina
  docker compose up                   →  la ejecuta en tu maquina

RAILWAY:
  push a GitHub                       →  Railway detecta el Dockerfile
  Railway ejecuta docker build        →  crea la imagen en sus servidores
  Railway ejecuta docker run          →  la ejecuta en la nube
```

La diferencia es DONDE corre la imagen, no la imagen en si. Por eso Railway es "simple":
tu solo escribes el Dockerfile, Railway hace el build y run.

---

## Resumen en una tabla

| Concepto | Que es | Archivo | Comando |
|----------|--------|---------|---------|
| Imagen | Receta empaquetada | Dockerfile | `docker build` |
| Contenedor | Imagen corriendo | — | `docker run` |
| Dockerfile | Instrucciones de construccion | `salon-service/Dockerfile` | — |
| Docker Compose | Orquestador multi-contenedor | `docker-compose.yml` | `docker compose up` |
| Volume | Datos persistentes | en docker-compose.yml | — |
| Port mapping | Puerta de acceso | `"8082:8082"` | — |
| Registry | Almacen de imagenes | Docker Hub, Railway | `docker push` |
| Cache | Reutilizar pasos anteriores | automatico | — |
| Multi-stage | Build grande → runtime pequeno | `FROM ... AS builder` | — |

## Cheatsheet de comandos

```bash
docker compose build              # Construir todas las imagenes
docker compose up -d              # Arrancar todo en segundo plano
docker compose down               # Parar todo
docker compose logs -f servicio   # Ver logs en tiempo real
docker compose ps                 # Ver estado de contenedores
docker compose restart servicio   # Reiniciar un servicio
docker compose exec mysql bash    # Entrar dentro de un contenedor
docker system prune               # Limpiar imagenes/contenedores viejos
```
