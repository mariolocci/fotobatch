# fotobatch

`fotobatch` e' un batch Maven che:

- legge un file di input con una lista di percorsi (uno per riga)
- esplora ogni percorso anche nelle sottocartelle
- estrae i metadati foto (EXIF/IPTC/XMP) da file JPEG e DNG (oltre ad altri formati supportati)
- calcola un hash SHA-256 del payload metadati
- salva e indicizza i metadati con Hibernate Search su backend Lucene embedded

## Requisiti

- Java 21+
- Maven 3.9+

## Build e test

```bash
mvn -Dskip.frontend=true clean test
mvn -DskipTests package
```

Note:

- `mvn package` ora esegue automaticamente la build React in `frontend/` e copia `dist/` in `static/` del jar.
- Per task backend-only (test rapidi), usa `-Dskip.frontend=true`.

## Uso CLI (batch)

1. Crea un file con i percorsi da elaborare, per esempio `paths.txt`:

```text
/home/user/Pictures
/home/user/altri-scatti
```

2. Esegui il batch:

```bash
java -jar target/fotobatch-0.0.1-SNAPSHOT.jar --fotobatch.cli.enabled=true --spring.main.web-application-type=none paths.txt
```

3. Indicizza e cerca subito nei metadati:

```bash
java -jar target/fotobatch-0.0.1-SNAPSHOT.jar --fotobatch.cli.enabled=true --spring.main.web-application-type=none paths.txt Nikon
```

## API REST ricerca

Avvio applicazione (modalita' web, default):

```bash
java -jar target/fotobatch-0.0.1-SNAPSHOT.jar
```

Apri poi la single page app su `http://localhost:8081/`.

Endpoint disponibili:

- `GET /api/search/metadata?q=<term>&limit=20`
- `GET /api/search/lucene?q=<lucene-query>&limit=20`
- `GET /api/search/month-year?month=<1-12>&year=<yyyy>&limit=20`

## API REST frontend gallery

- `GET /api/gallery/search?q=<term>&page=0&size=12&syntax=metadata|lucene`
- `GET /api/gallery/thumbnail?path=<absolute-file-path>&w=280&h=180`

`/api/gallery/search` restituisce una pagina con:

- risultati paginati (`items`, `page`, `size`, `hasNext`)
- `title` calcolato come `<localita da GPS> - <nome file>` quando coordinate disponibili
- `description` con metadati completi in JSON
- `thumbnailUrl` pronto da usare lato frontend

La localita' viene ricavata in best-effort con reverse geocoding OpenStreetMap Nominatim.

## Frontend React (single page)

E' presente un frontend React in `frontend/` con:

- search box stile Google
- risultati paginati
- thumbnail foto
- titolo composto da localita' + nome file
- descrizione con tutti i metadati

Avvio sviluppo frontend:

```bash
cd frontend
npm install
npm run dev
```

Il dev server frontend gira su `http://localhost:5174` e usa proxy `/api` verso `http://localhost:8081`.

Build frontend:

```bash
cd frontend
npm run build
```

In alternativa non serve build manuale se usi `mvn package`: il frontend viene gia' compilato e incorporato nel jar.

## Dati prodotti

- DB H2 locale: `target/fotobatch-db*`
- Indice Lucene embedded: `target/lucene-index/`

Ogni documento indicizzato contiene:

- percorso assoluto file
- hash metadati SHA-256
- estensione file
- metadati completi in JSON
- metadati normalizzati in testo per ricerca full-text

Inoltre, l'entita' `PhotoMetadataEntity` espone campi dedicati ricercabili (Hibernate Search) come:

- `title`, `description`
- `cameraMake`, `cameraModel`, `lensModel`
- `dateTimeOriginal`, `iso`, `aperture`, `shutterSpeed`, `focalLength`
- `gpsLatitude`, `gpsLongitude`
- `keywords`, `artist`, `copyrightText`, `software`
