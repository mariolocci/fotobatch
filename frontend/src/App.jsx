import { useEffect, useMemo, useState } from "react";

const PAGE_SIZE = 12;
const SEARCH_PAGE = "search";
const INDEXING_PAGE = "indexing";

function App() {
  const [activePage, setActivePage] = useState(SEARCH_PAGE);
  const [input, setInput] = useState("");
  const [query, setQuery] = useState("");
  const [syntax, setSyntax] = useState("metadata");
  const [page, setPage] = useState(0);
  const [items, setItems] = useState([]);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [resourcePath, setResourcePath] = useState("");
  const [indexing, setIndexing] = useState(false);
  const [indexingError, setIndexingError] = useState("");
  const [indexingResult, setIndexingResult] = useState(null);
  const [facets, setFacets] = useState({ monthYears: [], localities: [] });
  const [selectedMonthYear, setSelectedMonthYear] = useState("");
  const [selectedLocality, setSelectedLocality] = useState("");
  const [selectedItem, setSelectedItem] = useState(null);

  const canSearch = useMemo(() => input.trim().length > 0, [input]);

  useEffect(() => {
    let cancelled = false;

    async function loadConfig() {
      try {
        const response = await fetch("/api/indexing/config");
        if (!response.ok) {
          return;
        }
        const data = await response.json();
        if (!cancelled) {
          setResourcePath(data.resourcePath || "");
        }
      } catch {
        // Config fetch is best-effort.
      }
    }

    async function loadFacets() {
      try {
        const response = await fetch("/api/gallery/facets");
        if (!response.ok) {
          return;
        }
        const data = await response.json();
        if (!cancelled) {
          setFacets({
            monthYears: data.monthYears ?? [],
            localities: data.localities ?? []
          });
        }
      } catch {
        // Facets are best-effort.
      }
    }

    loadConfig();
    loadFacets();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!query && !selectedMonthYear && !selectedLocality) {
      return;
    }

    const controller = new AbortController();

    async function load() {
      setLoading(true);
      setError("");
      try {
        const params = new URLSearchParams({
          page: String(page),
          size: String(PAGE_SIZE),
          syntax
        });
        if (query) {
          params.set("q", query);
        }
        if (selectedMonthYear) {
          const [year, month] = selectedMonthYear.split("-");
          params.set("year", year);
          params.set("month", String(Number(month)));
        }
        if (selectedLocality) {
          params.set("locality", selectedLocality);
        }

        const response = await fetch(`/api/gallery/search?${params.toString()}`, {
          signal: controller.signal
        });

        if (!response.ok) {
          throw new Error(`Search failed (${response.status})`);
        }

        const data = await response.json();
        setItems(data.items ?? []);
        setHasNext(Boolean(data.hasNext));
      } catch (e) {
        if (e.name !== "AbortError") {
          setError(e.message || "Unexpected error");
        }
      } finally {
        setLoading(false);
      }
    }

    load();

    return () => controller.abort();
  }, [query, page, syntax, selectedMonthYear, selectedLocality]);

  useEffect(() => {
    function onKeyDown(event) {
      if (event.key === "Escape") {
        setSelectedItem(null);
      }
    }

    if (selectedItem) {
      window.addEventListener("keydown", onKeyDown);
      return () => window.removeEventListener("keydown", onKeyDown);
    }

    return undefined;
  }, [selectedItem]);

  function onSubmit(event) {
    event.preventDefault();
    if (!canSearch) {
      return;
    }
    setPage(0);
    setSelectedMonthYear("");
    setSelectedLocality("");
    setQuery(input.trim());
  }

  function onSelectMonthYear(value) {
    setPage(0);
    setSelectedLocality("");
    setSelectedMonthYear(value);
    setInput("");
    setQuery("");
  }

  function onSelectLocality(value) {
    setPage(0);
    setSelectedMonthYear("");
    setSelectedLocality(value);
    setInput("");
    setQuery("");
  }

  function clearQuickFilters() {
    setPage(0);
    setSelectedMonthYear("");
    setSelectedLocality("");
  }

  function onPrev() {
    setPage((value) => Math.max(0, value - 1));
  }

  function onNext() {
    if (hasNext) {
      setPage((value) => value + 1);
    }
  }

  function openPhotoModal(item) {
    setSelectedItem(item);
  }

  function closePhotoModal() {
    setSelectedItem(null);
  }

  async function onStartIndexing() {
    setIndexing(true);
    setIndexingError("");
    try {
      const response = await fetch("/api/indexing/run", {
        method: "POST"
      });
      if (!response.ok) {
        throw new Error(`Indicizzazione fallita (${response.status})`);
      }
      const data = await response.json();
      setIndexingResult(data);
      const facetsResponse = await fetch("/api/gallery/facets");
      if (facetsResponse.ok) {
        const facetsData = await facetsResponse.json();
        setFacets({
          monthYears: facetsData.monthYears ?? [],
          localities: facetsData.localities ?? []
        });
      }
    } catch (e) {
      setIndexingError(e.message || "Errore durante l'indicizzazione");
    } finally {
      setIndexing(false);
    }
  }

  function formatShotDate(raw) {
    if (!raw) {
      return "N/D";
    }
    const exifMatch = raw.match(/^(\d{4})[:\/-](\d{2})[:\/-](\d{2})/);
    if (exifMatch) {
      const [, y, m, d] = exifMatch;
      return `${d}/${m}/${y}`;
    }
    return raw;
  }

  function formatMonthYearKey(value) {
    const parts = value.split("-");
    if (parts.length !== 2) {
      return value;
    }
    return `${parts[1]}/${parts[0]}`;
  }

  function buildPhotoApiUrl(filePath) {
    return `/api/gallery/photo?path=${encodeURIComponent(filePath)}`;
  }

  function buildThumbnailApiUrl(filePath) {
    return `/api/gallery/thumbnail?path=${encodeURIComponent(filePath)}`;
  }

  return (
    <main className="app">
      <nav className="top-nav">
        <button
          type="button"
          className={activePage === SEARCH_PAGE ? "tab active" : "tab"}
          onClick={() => setActivePage(SEARCH_PAGE)}
        >
          Ricerca
        </button>
        <button
          type="button"
          className={activePage === INDEXING_PAGE ? "tab active" : "tab"}
          onClick={() => setActivePage(INDEXING_PAGE)}
        >
          Indicizzazione
        </button>
      </nav>

      {activePage === SEARCH_PAGE && (
        <>
      <section className={query || selectedMonthYear || selectedLocality ? "search-box compact" : "search-box"}>
        <h1>FotoBatch Search</h1>
        <form onSubmit={onSubmit}>
          <input
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder="Cerca metadati, macchina fotografica, localita..."
            aria-label="search"
          />
          <div className="search-actions">
            <select value={syntax} onChange={(event) => setSyntax(event.target.value)}>
              <option value="metadata">Ricerca naturale</option>
              <option value="lucene">Sintassi Lucene</option>
            </select>
            <button type="submit" disabled={!canSearch || loading}>
              Cerca
            </button>
          </div>
        </form>
      </section>

      {(query || selectedMonthYear || selectedLocality) && (
        <section className="results-layout">
          <section className="results">
          <header>
            <p>
              {query && <>Query: <strong>{query}</strong></>}
              {!query && selectedMonthYear && <>Filtro data: <strong>{formatMonthYearKey(selectedMonthYear)}</strong></>}
              {!query && selectedLocality && <>Filtro localita: <strong>{selectedLocality}</strong></>}
              {" - "}Pagina {page + 1}
            </p>
          </header>

          {loading && <p className="hint">Ricerca in corso...</p>}
          {error && <p className="error">{error}</p>}
          {!loading && !error && items.length === 0 && <p className="hint">Nessun risultato</p>}

          <ul>
            {items.map((item) => (
              <li key={item.filePath}>
                <button
                  type="button"
                  className="thumb-button"
                  onClick={() => openPhotoModal(item)}
                  title="Apri anteprima foto"
                >
                  <img
                    src={buildThumbnailApiUrl(item.filePath)}
                    alt={item.title}
                    loading="lazy"
                    onError={(event) => {
                      event.currentTarget.src =
                        "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='280' height='180'%3E%3Crect width='100%25' height='100%25' fill='%23ececec'/%3E%3Ctext x='50%25' y='50%25' dominant-baseline='middle' text-anchor='middle' fill='%23666' font-size='14'%3ENo preview%3C/text%3E%3C/svg%3E";
                    }}
                  />
                </button>
                <div className="result-content">
                  <h3>{item.title}</h3>
                  <div className="meta-grid">
                    <p><strong>Data:</strong> {formatShotDate(item.shotDate)}</p>
                    <p><strong>Fotocamera:</strong> {item.camera || "N/D"}</p>
                    <p><strong>Risoluzione:</strong> {item.resolution || "N/D"}</p>
                    <p><strong>Formato:</strong> {(item.format || "N/D").toUpperCase()}</p>
                    <p><strong>Dimensione:</strong> {item.fileSize || "N/D"}</p>
                  </div>
                  <p className="path">{item.filePath}</p>
                  {Array.isArray(item.duplicatePaths) && item.duplicatePaths.length > 0 && (
                    <details>
                      <summary>Duplicati ({item.duplicatePaths.length})</summary>
                      <ul>
                        {item.duplicatePaths.map((duplicatePath) => (
                          <li key={duplicatePath} className="path">{duplicatePath}</li>
                        ))}
                      </ul>
                    </details>
                  )}
                  <details>
                    <summary>Metadati completi</summary>
                    <pre>{item.description}</pre>
                  </details>
                </div>
              </li>
            ))}
          </ul>

          <footer className="pager">
            <button type="button" onClick={onPrev} disabled={page === 0 || loading}>
              Precedente
            </button>
            <button type="button" onClick={onNext} disabled={!hasNext || loading}>
              Successiva
            </button>
          </footer>
          </section>

          <aside className="facet-sidebar">
            <div className="facet-header">
              <h3>Filtri rapidi</h3>
              <button type="button" className="link-button" onClick={clearQuickFilters}>
                Reset
              </button>
            </div>

            <section className="facet-group">
              <h4>Date (mese/anno)</h4>
              <ul>
                {facets.monthYears.map((facet) => (
                  <li key={facet.key}>
                    <button
                      type="button"
                      className={selectedMonthYear === facet.key ? "facet active" : "facet"}
                      onClick={() => onSelectMonthYear(facet.key)}
                    >
                      {facet.label} <span>({facet.count})</span>
                    </button>
                  </li>
                ))}
              </ul>
            </section>

            <section className="facet-group">
              <h4>Localita</h4>
              <ul>
                {facets.localities.map((facet) => (
                  <li key={facet.key}>
                    <button
                      type="button"
                      className={selectedLocality === facet.key ? "facet active" : "facet"}
                      onClick={() => onSelectLocality(facet.key)}
                    >
                      {facet.label} <span>({facet.count})</span>
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          </aside>
        </section>
      )}
        </>
      )}

      {activePage === INDEXING_PAGE && (
        <section className="indexing-panel">
          <h2>Avvio Indicizzazione</h2>
          <p>
            I percorsi vengono letti dal file nelle resources:
            <code>{resourcePath || " indexing-paths.txt"}</code>
          </p>
          <button type="button" onClick={onStartIndexing} disabled={indexing}>
            {indexing ? "Indicizzazione in corso..." : "Avvia indicizzazione"}
          </button>

          {indexingError && <p className="error">{indexingError}</p>}

          {indexingResult && (
            <div className="indexing-result">
              <p>
                <strong>Resource:</strong> {indexingResult.resourcePath}
              </p>
              <p>
                <strong>Indicizzati:</strong> {indexingResult.indexedFiles}
              </p>
              <p>
                <strong>Falliti:</strong> {indexingResult.failedFiles}
              </p>
              <p>
                <strong>Root mancanti:</strong> {indexingResult.missingRoots}
              </p>
            </div>
          )}
        </section>
      )}

      {selectedItem && (
        <div className="photo-modal-overlay" onClick={closePhotoModal} role="presentation">
          <section
            className="photo-modal"
            role="dialog"
            aria-modal="true"
            aria-label="Anteprima foto"
            onClick={(event) => event.stopPropagation()}
          >
            <button type="button" className="photo-modal-close" onClick={closePhotoModal}>
              Chiudi
            </button>
            <img
              className="photo-modal-image"
              src={buildPhotoApiUrl(selectedItem.filePath)}
              alt={selectedItem.title}
            />
            <p className="photo-modal-title">{selectedItem.title}</p>
          </section>
        </div>
      )}
    </main>
  );
}

export default App;
