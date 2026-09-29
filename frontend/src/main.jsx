import React, {useEffect, useState} from 'react';
import {createRoot} from 'react-dom/client';
import './styles.css';

async function api(url) {
    const response = await fetch(url);
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || 'Ошибка API');
    return data;
}

function App() {
    const [data, setData] = useState({participants: [], textbooks: [], codes: [], orders: []});
    const [error, setError] = useState('');

    async function load() {
        try {
            setError('');
            const [participants, textbooks, codes, orders] = await Promise.all([
                api('/api/participants'),
                api('/api/textbooks'),
                api('/api/marking-codes'),
                api('/api/code-orders')
            ]);
            setData({participants, textbooks, codes, orders});
        } catch (e) {
            setError(e.message);
        }
    }

    useEffect(() => {
        load();
    }, []);

    return (
        <div className="app">
            <aside>
                <div className="logo">ТЕКШЕР <span>AIS</span></div>
                <nav>
                    {['Главная', 'Участники', 'Учебники', 'Коды маркировки', 'Заказы КМ', 'Операции', 'Документы', 'Финансы', 'История', 'Настройки']
                        .map((item, index) => <a className={index === 0 ? 'active' : ''} key={item}>{item}</a>)}
                </nav>
            </aside>
            <main>
                <header>
                    <div><h1>АИС маркировки учебников</h1><p>Рабочая панель MVP</p></div>
                    <button onClick={load}>Обновить</button>
                </header>
                {error && <div className="error">{error}</div>}
                <section className="cards">
                    <Card title="Учебники" value={data.textbooks.length}/>
                    <Card title="Коды" value={data.codes.length}/>
                    <Card title="Заказы КМ" value={data.orders.length}/>
                    <Card title="Участники" value={data.participants.length}/>
                </section>
                <section className="panel">
                    <h2>Последние учебники</h2>
                    <table>
                        <thead>
                        <tr>
                            <th>Название</th>
                            <th>GTIN</th>
                            <th>Автор</th>
                            <th>Статус</th>
                        </tr>
                        </thead>
                        <tbody>{data.textbooks.slice(-10).reverse().map(t =>
                            <tr key={t.id}>
                                <td>{t.title}</td>
                                <td>{t.gtin}</td>
                                <td>{t.author}</td>
                                <td><span className="status">{t.status}</span></td>
                            </tr>
                        )}</tbody>
                    </table>
                    {!data.textbooks.length && <div className="empty">Карточек пока нет</div>}
                </section>
            </main>
        </div>
    );
}

function Card({title, value}) {
    return <div className="card">
        <div>{title}</div>
        <strong>{value}</strong></div>;
}

createRoot(document.getElementById('root')).render(<App/>);
