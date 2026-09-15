let token, booking, liveAbort;
const selected = new Set();
const $ = id => document.getElementById(id);
const show = x => $('result').textContent = JSON.stringify(x, null, 2);

async function api(path, method = 'GET', body, key) {
    const r = await fetch(path, {
        method,
        headers: {
            ...(token && path !== '/local/token' ? {Authorization: 'Bearer ' + token} : {}),
            'Content-Type': 'application/json', ...(key ? {'Idempotency-Key': key} : {})
        },
        body: body ? JSON.stringify(body) : undefined
    });
    const data = await r.json();
    if (!r.ok) throw data;
    return data;
}

function handle(fn) {
    return async () => {
        try {
            await fn();
        } catch (e) {
            show(e);
        }
    };
}

$('login').onclick = handle(async () => {
    const t = await api('/local/token', 'POST', {username: $('user').value, password: $('password').value});
    token = t.access_token;
    $('password').value = '';
    await browse();
});

async function browse() {
    const rows = await api('/api/shows');
    $('shows').replaceChildren(...rows.map(s => {
        const o = document.createElement('option');
        o.value = s.id;
        o.textContent = s.title + ' — ' + s.startsAt;
        return o;
    }));
    if (rows.length) startLive();
}

$('browse').onclick = handle(browse);
$('shows').onchange = startLive;

function render(rows) {
    $('seats').replaceChildren(...rows.map(s => {
        const b = document.createElement('button');
        b.textContent = s.label + ' ' + s.status;
        b.className = 'seat' + (selected.has(s.label) ? ' selected' : '');
        b.disabled = s.status !== 'AVAILABLE';
        b.onclick = () => {
            selected.has(s.label) ? selected.delete(s.label) : selected.add(s.label);
            render(rows);
        };
        return b;
    }));
}

async function startLive() {
    liveAbort?.abort();
    liveAbort = new AbortController();
    const signal = liveAbort.signal;
    selected.clear();
    while (!signal.aborted) {
        try {
            $('connection').textContent = 'Connecting';
            const r = await fetch('/api/live/shows/' + $('shows').value, {
                headers: {Authorization: 'Bearer ' + token},
                signal
            });
            if (!r.ok) throw new Error('Sign in again: ' + r.status);
            $('connection').textContent = 'Live';
            const reader = r.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            while (true) {
                const {done, value} = await reader.read();
                if (done) break;
                buffer += decoder.decode(value, {stream: true}).replaceAll('\r', '');
                let cut;
                while ((cut = buffer.indexOf('\n\n')) >= 0) {
                    const event = buffer.slice(0, cut);
                    buffer = buffer.slice(cut + 2);
                    const data = event.split('\n').filter(l => l.startsWith('data:')).map(l => l.slice(5)).join('\n');
                    if (data) render(JSON.parse(data));
                }
            }
        } catch (e) {
            if (signal.aborted) return;
            $('connection').textContent = e.message;
        }
        await new Promise(r => setTimeout(r, 2000));
    }
}

$('hold').onclick = handle(async () => {
    booking = await api('/api/bookings', 'POST', {showId: $('shows').value, seats: [...selected]}, crypto.randomUUID());
    selected.clear();
    show(booking);
});
$('pay').onclick = handle(async () => {
    if (!booking) throw 'Reserve seats first';
    show(await api('/api/bookings/' + booking.id + '/payment', 'POST', {mode: $('mode').value}));
    for (let i = 0; i < 20; i++) {
        await new Promise(r => setTimeout(r, 1000));
        booking = await api('/api/bookings/' + booking.id);
        show(booking);
        if (!['PAYMENT_PENDING', 'HELD'].includes(booking.status)) break;
    }
});
$('cancel').onclick = handle(async () => show(await api('/api/bookings/' + booking.id + '/cancel', 'POST')));
$('tickets').onclick = handle(async () => show(await api('/api/bookings/' + booking.id + '/tickets')));
