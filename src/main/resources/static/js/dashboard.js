(() => {
    'use strict';
    const root = document.getElementById('dashboard');
    if (!root) return;
    const byId = id => document.getElementById(id);
    const form = byId('dashboard-filters');
    const money = new Intl.NumberFormat('pt-BR', {style: 'currency', currency: 'BRL'});
    const number = new Intl.NumberFormat('pt-BR');
    let applied = new URLSearchParams();
    let request;
    let sequence = 0;
    let timer;
    const text = (id, value) => { byId(id).textContent = value; };
    const date = value => value.split('-').reverse().join('/');

    function row(values) {
        const tr = document.createElement('tr');
        values.forEach(value => { const td = document.createElement('td'); td.textContent = value; tr.append(td); });
        return tr;
    }

    function render(data) {
        const k = data.indicadores;
        text('metric-revenue', money.format(k.faturamento));
        text('metric-orders', number.format(k.pedidos));
        text('metric-completed', `${number.format(k.vendasConcluidas)} vendas finalizadas válidas`);
        text('metric-ticket', money.format(k.ticketMedio));
        text('metric-active', number.format(k.emAndamento));
        text('metric-canceled', `${number.format(k.cancelados)} pedidos cancelados`);
        const maximum = Math.max(1, ...data.vendas.map(v => v.faturamento));
        const chart = byId('sales-chart');
        chart.style.gap = data.vendas.length > 90 ? '0' : '4px';
        const table = byId('sales-table');
        chart.replaceChildren(); table.replaceChildren();
        const labelStep = Math.ceil(data.vendas.length / 8);
        data.vendas.forEach((v, index) => {
            const column = document.createElement('div'); column.className = 'sales-column';
            column.title = `${date(v.periodo)}: ${money.format(v.faturamento)} · ${v.quantidade} vendas`;
            const bar = document.createElement('div'); bar.className = 'sales-bar';
            bar.style.height = `${v.faturamento / maximum * 175}px`;
            const label = document.createElement('span');
            if (index === data.vendas.length - 1 || (index % labelStep === 0
                    && (index === 0 || index < data.vendas.length - labelStep))) {
                label.textContent = data.agrupamento === 'MES' ? v.periodo.slice(0, 7).split('-').reverse().join('/') : date(v.periodo).slice(0, 5);
            }
            column.append(bar, label); chart.append(column);
            table.append(row([date(v.periodo), number.format(v.quantidade), money.format(v.faturamento)]));
        });
        const statuses = byId('status-chart'); statuses.replaceChildren();
        data.situacoes.forEach(s => {
            const li = document.createElement('li');
            const label = document.createElement('div'); label.className = 'status-label';
            const name = document.createElement('span'); name.textContent = s.descricao;
            const count = document.createElement('strong'); count.textContent = number.format(s.quantidade);
            label.append(name, count);
            const track = document.createElement('div'); track.className = 'status-track'; track.setAttribute('aria-hidden', 'true');
            const fill = document.createElement('div'); fill.className = 'status-fill';
            fill.style.width = `${s.quantidade / Math.max(1, k.pedidos) * 100}%`;
            track.append(fill); li.append(label, track); statuses.append(li);
        });
        const products = byId('products-table'); products.replaceChildren();
        data.produtos.forEach((p, i) => products.append(row([i + 1, p.nome, number.format(p.quantidade)])));
        byId('ranking-empty').hidden = data.produtos.length > 0;
        byId('dashboard-warning').hidden = data.pedidosInconsistentes === 0;
        text('dashboard-warning', `${data.pedidosInconsistentes} pedido(s) com dados incompletos ou inválidos. Valores ausentes não são estimados; confira os registros de origem.`);
        text('dashboard-updated', `Atualizado em ${date(data.atualizadoEm.slice(0, 10))} às ${data.atualizadoEm.slice(11, 19)}`);
        text('dashboard-message', k.pedidos === 0 ? 'Nenhum pedido encontrado para os filtros selecionados.' : `${k.pedidos} pedido(s) de ${date(data.inicio)} a ${date(data.fim)}.`);
        if (!form.elements.inicio.value) form.elements.inicio.value = data.inicio;
        if (!form.elements.fim.value) form.elements.fim.value = data.fim;
        byId('dashboard-results').hidden = false;
    }

    async function load() {
        if (request) request.abort();
        const current = ++sequence;
        request = new AbortController();
        const timeout = setTimeout(() => request && current === sequence && request.abort(), 15000);
        root.setAttribute('aria-busy', 'true');
        text('dashboard-message', 'Carregando indicadores…');
        byId('dashboard-refresh').disabled = true;
        try {
            const response = await fetch(`/api/dashboard?${applied}`, {signal: request.signal, headers: {Accept: 'application/json'}, cache: 'no-store'});
            if (response.status === 401) throw new Error('Sessão expirada. Entre novamente para consultar o dashboard.');
            if (response.status === 403) throw new Error('Seu perfil não tem acesso ao dashboard.');
            if (!response.ok) {
                let error;
                try { error = await response.json(); } catch (_) { /* Erro sem JSON: mensagem padrão. */ }
                throw new Error(error?.erro || 'Não foi possível consultar os dados. Tente atualizar novamente.');
            }
            const data = await response.json();
            if (current === sequence) render(data);
        } catch (error) {
            if (current !== sequence) return;
            byId('dashboard-results').hidden = true;
            byId('dashboard-warning').hidden = true;
            text('dashboard-updated', 'Dados indisponíveis');
            text('dashboard-message', error.name === 'AbortError'
                ? 'A consulta demorou demais. Tente atualizar novamente.'
                : error instanceof TypeError
                    ? 'Falha de conexão. Verifique se o servidor está disponível e tente atualizar novamente.'
                    : error.message);
            byId('dashboard-message').setAttribute('role', 'alert');
        } finally {
            clearTimeout(timeout);
            if (current === sequence) { root.setAttribute('aria-busy', 'false'); byId('dashboard-refresh').disabled = false; }
        }
    }

    form.addEventListener('submit', event => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        applied = new URLSearchParams(new FormData(form));
        if (!applied.get('status')) applied.delete('status');
        byId('dashboard-message').setAttribute('role', 'status');
        load();
    });
    byId('dashboard-refresh').addEventListener('click', load);
    byId('dashboard-auto').addEventListener('change', event => {
        clearInterval(timer);
        if (event.target.checked) timer = setInterval(() => { if (!document.hidden && !byId('dashboard-refresh').disabled) load(); }, 60000);
    });
    window.addEventListener('pagehide', () => { clearInterval(timer); if (request) request.abort(); });
    load();
})();
