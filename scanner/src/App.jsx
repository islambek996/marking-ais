import { useEffect, useRef, useState } from "react";
import { BrowserMultiFormatReader } from "@zxing/browser";
import { lookupCode } from "./api";
import { parseGs1 } from "./gs1";

const reader = new BrowserMultiFormatReader();

function statusLabel(status) {
  const labels = {
    EMITTED: "Выпущен",
    APPLIED: "Нанесён",
    IN_CIRCULATION: "В обороте",
    WITHDRAWN: "Выведен из оборота"
  };
  return labels[status] || status;
}

function App() {
  const videoRef = useRef(null);
  const controlsRef = useRef(null);
  const [devices, setDevices] = useState([]);
  const [deviceId, setDeviceId] = useState("");
  const [scanning, setScanning] = useState(false);
  const [message, setMessage] = useState("Наведите камеру на DataMatrix");
  const [raw, setRaw] = useState("");
  const [parsed, setParsed] = useState(null);
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [manual, setManual] = useState("");

  useEffect(() => {
    let active = true;

    reader.listVideoInputDevices()
      .then(list => {
        if (!active) return;
        setDevices(list);
        const back = list.find(d => /back|rear|environment|зад/i.test(d.label));
        setDeviceId(back?.deviceId || list[0]?.deviceId || "");
      })
      .catch(() => {
        if (active) setError("Не удалось получить доступ к камере. Разрешите камеру в браузере.");
      });

    return () => {
      active = false;
      controlsRef.current?.stop();
    };
  }, []);

  async function checkRaw(value) {
    const result = parseGs1(value);
    setRaw(result.raw);
    setParsed(result);
    setData(null);
    setError("");

    if (!result.valid) {
      setError("Не удалось определить GTIN и серийный номер GS1 DataMatrix.");
      return;
    }

    setLoading(true);
    try {
      const response = await lookupCode(result.gtin, result.serial);
      setData(response);
      setMessage("Код успешно проверен");
    } catch (e) {
      setError(e.message || "Ошибка проверки кода");
      setMessage("Код считан, но не найден в системе");
    } finally {
      setLoading(false);
    }
  }

  async function startScanner() {
    setError("");
    setMessage("Наведите камеру на DataMatrix");
    setScanning(true);

    try {
      controlsRef.current?.stop();
      controlsRef.current = await reader.decodeFromVideoDevice(
        deviceId || undefined,
        videoRef.current,
        (result, error) => {
          if (result) {
            const value = result.getText();
            setScanning(false);
            controlsRef.current?.stop();
            checkRaw(value);
          }
        }
      );
    } catch (e) {
      setScanning(false);
      setError("Камера недоступна. Проверьте разрешение браузера и HTTPS.");
    }
  }

  function stopScanner() {
    controlsRef.current?.stop();
    controlsRef.current = null;
    setScanning(false);
    setMessage("Сканирование остановлено");
  }

  async function manualCheck(event) {
    event.preventDefault();
    if (!manual.trim()) return;
    await checkRaw(manual.trim());
  }

  return (
    <div className="app">
      <header className="header">
        <div className="brand">
          <div className="brand-mark">Т</div>
          <div>
            <div className="brand-title">Текшер</div>
            <div className="brand-subtitle">Проверка кода маркировки</div>
          </div>
        </div>
        <div className="header-status">
          <span className="status-dot" />
          AIS Marking
        </div>
      </header>

      <main className="main">
        <section className="hero">
          <div>
            <span className="eyebrow">DATA MATRIX</span>
            <h1>Проверьте код маркировки</h1>
            <p>
              Отсканируйте DataMatrix камерой смартфона, чтобы получить
              информацию о коде и учебнике.
            </p>
          </div>
        </section>

        <section className="scanner-card">
          <div className="camera-wrap">
            <video ref={videoRef} className="camera" muted playsInline />
            <div className="scan-frame">
              <span />
              <span />
              <span />
              <span />
            </div>
            {!scanning && (
              <div className="camera-placeholder">
                <div className="camera-icon">⌗</div>
                <strong>Камера готова</strong>
                <small>{message}</small>
              </div>
            )}
          </div>

          <div className="scanner-actions">
            {devices.length > 1 && (
              <select value={deviceId} onChange={e => setDeviceId(e.target.value)}>
                {devices.map((device, index) => (
                  <option key={device.deviceId} value={device.deviceId}>
                    {device.label || `Камера ${index + 1}`}
                  </option>
                ))}
              </select>
            )}

            {!scanning ? (
              <button className="primary" onClick={startScanner}>
                Сканировать код
              </button>
            ) : (
              <button className="secondary" onClick={stopScanner}>
                Остановить камеру
              </button>
            )}
          </div>

          <form className="manual-form" onSubmit={manualCheck}>
            <label>Или вставьте содержимое DataMatrix</label>
            <div className="manual-row">
              <input
                value={manual}
                onChange={e => setManual(e.target.value)}
                placeholder="01...21..."
              />
              <button className="secondary" type="submit" disabled={loading}>
                Проверить
              </button>
            </div>
          </form>
        </section>

        {error && (
          <section className="alert error">
            <strong>Проверка не выполнена</strong>
            <span>{error}</span>
          </section>
        )}

        {parsed && (
          <section className="result-card">
            <div className="result-heading">
              <div>
                <span className="eyebrow">GS1 DATA MATRIX</span>
                <h2>Результат сканирования</h2>
              </div>
              <span className={parsed.valid ? "pill success" : "pill danger"}>
                {parsed.valid ? "Код распознан" : "Ошибка формата"}
              </span>
            </div>

            <div className="grid">
              <Info label="GTIN" value={parsed.gtin || "–"} mono />
              <Info label="Серийный номер" value={parsed.serial || "–"} mono />
              <Info label="AI 01" value={parsed.ai["01"] || "–"} />
              <Info label="AI 21" value={parsed.ai["21"] || "–"} />
            </div>

            <div className="raw-block">
              <span>Исходное содержимое</span>
              <code>{raw || "–"}</code>
            </div>
          </section>
        )}

        {loading && (
          <section className="loading">
            <div className="spinner" />
            Проверяем код в AIS…
          </section>
        )}

        {data && (
          <section className="result-card verified">
            <div className="result-heading">
              <div>
                <span className="eyebrow">AIS MARKING</span>
                <h2>Информация о коде</h2>
              </div>
              <span className="pill success">Код найден</span>
            </div>

            <div className="status-banner">
              <div>
                <span>Статус кода</span>
                <strong>{statusLabel(data.code.status)}</strong>
              </div>
              <div className="status-check">✓</div>
            </div>

            <h3>Код маркировки</h3>
            <div className="grid">
              <Info label="GTIN" value={data.code.gtin} mono />
              <Info label="Серийный номер" value={data.code.serial} mono />
              <Info label="Payload" value={data.code.payload} mono />
              <Info label="Дата выпуска" value={formatDate(data.code.createdAt)} />
            </div>

            <h3>Учебник</h3>
            <div className="grid">
              <Info label="Название" value={data.textbook.title} />
              <Info label="Автор" value={data.textbook.author} />
              <Info label="Класс" value={data.textbook.schoolClass} />
              <Info label="Предмет" value={data.textbook.subject} />
              <Info label="Издательство" value={data.textbook.publisher} />
              <Info label="Год издания" value={data.textbook.year} />
              <Info label="Язык" value={data.textbook.language} />
              <Info label="ISBN" value={data.textbook.isbn} mono />
              <Info label="Тираж" value={data.textbook.printRun} />
              <Info label="Возрастная категория" value={data.textbook.ageCategory} />
              <Info label="Страна производства" value={data.textbook.countryOfProduction} />
              <Info label="Производитель" value={data.textbook.manufacturer} />
            </div>

            <h3>Владелец</h3>
            <div className="owner">
              <strong>{data.participant.name}</strong>
              <span>ИНН: {data.participant.inn}</span>
              <span>{data.participant.legalAddress || "Адрес не указан"}</span>
            </div>

            {data.history?.length > 0 && (
              <>
                <h3>История кода</h3>
                <div className="history">
                  {data.history.map(item => (
                    <div className="history-item" key={item.id}>
                      <div className="history-dot" />
                      <div>
                        <strong>{item.operation}</strong>
                        <span>{item.oldStatus} → {item.newStatus}</span>
                        <small>{formatDate(item.createdAt)}</small>
                      </div>
                    </div>
                  ))}
                </div>
              </>
            )}
          </section>
        )}
      </main>

      <footer>
        <span>Текшер – система проверки кодов маркировки</span>
        <span>Кыргызская Республика</span>
      </footer>
    </div>
  );
}

function Info({ label, value, mono = false }) {
  return (
    <div className="info">
      <span>{label}</span>
      <strong className={mono ? "mono" : ""}>{value ?? "–"}</strong>
    </div>
  );
}

function formatDate(value) {
  if (!value) return "–";
  return new Date(value).toLocaleString("ru-RU");
}

export default App;
