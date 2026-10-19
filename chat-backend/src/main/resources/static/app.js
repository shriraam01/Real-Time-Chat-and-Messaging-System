'use strict';

/*
 * Real-Time Chat - browser client.
 * Plain JavaScript, no build step. Talks to the Spring Boot backend over:
 *   - REST  (login, users, conversations, message history)
 *   - STOMP over WebSocket (sending messages, live delivery, presence, receipts)
 * All text coming from users is inserted with textContent, never innerHTML.
 */
(function () {
  const PAGE_SIZE = 30;

  const state = {
    token: sessionStorage.getItem('token'),
    me: readJson(sessionStorage.getItem('me')),
    conversations: [],
    currentId: null,
    history: {},            // conversationId -> { items, page, last, loaded, loading }
    client: null,
    connected: false,
    modalRefresh: null,     // set while the group-info modal is open
    leaving: new Set()      // groups this user is leaving right now
  };

  const $ = (id) => document.getElementById(id);

  // ------------------------------------------------------------------ helpers

  function readJson(text) {
    try { return text ? JSON.parse(text) : null; } catch (e) { return null; }
  }

  // tiny DOM builder: el('div', { class: 'x', onclick: fn }, child, 'text', ...)
  function el(tag, props, ...children) {
    const node = document.createElement(tag);
    if (props) {
      for (const [key, value] of Object.entries(props)) {
        if (value === null || value === undefined || value === false) continue;
        if (key === 'class') node.className = value;
        else if (key.startsWith('on') && typeof value === 'function') node.addEventListener(key.slice(2), value);
        else node.setAttribute(key, value === true ? '' : value);
      }
    }
    for (const child of children.flat()) {
      if (child === null || child === undefined || child === false) continue;
      node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return node;
  }

  function parseDate(value) {
    if (!value) return null;
    if (Array.isArray(value)) {   // in case the server sends [y, m, d, h, mi, s]
      return new Date(value[0], value[1] - 1, value[2], value[3] || 0, value[4] || 0, value[5] || 0);
    }
    const d = new Date(value);
    return isNaN(d.getTime()) ? null : d;
  }

  function startOfDay(d) { return new Date(d.getFullYear(), d.getMonth(), d.getDate()); }
  function dayDiff(d) { return Math.round((startOfDay(new Date()) - startOfDay(d)) / 86400000); }

  function formatTime(value) {
    const d = parseDate(value);
    return d ? d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
  }

  function dayLabel(d) {
    const diff = dayDiff(d);
    if (diff === 0) return 'Today';
    if (diff === 1) return 'Yesterday';
    return d.toLocaleDateString([], { day: 'numeric', month: 'short', year: 'numeric' });
  }

  function listTime(value) {
    const d = parseDate(value);
    if (!d) return '';
    const diff = dayDiff(d);
    if (diff === 0) return formatTime(value);
    if (diff === 1) return 'Yesterday';
    return d.toLocaleDateString([], { day: 'numeric', month: 'short' });
  }

  function lastSeenText(value) {
    const d = parseDate(value);
    if (!d) return 'offline';
    const diff = dayDiff(d);
    if (diff === 0) return 'last seen today at ' + formatTime(value);
    if (diff === 1) return 'last seen yesterday at ' + formatTime(value);
    return 'last seen ' + d.toLocaleDateString([], { day: 'numeric', month: 'short' });
  }

  function maxNum(a, b) { return Math.max(a || 0, b || 0); }

  let toastTimer = null;
  function toast(message) {
    const box = $('toast');
    box.textContent = message;
    box.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { box.hidden = true; }, 3500);
  }

  function errorMessage(data, status) {
    if (data && typeof data.error === 'string') return data.error;
    if (data && typeof data === 'object') {
      const texts = Object.values(data).filter((v) => typeof v === 'string');
      if (texts.length) return texts.join(', ');
    }
    return 'Request failed (' + status + ')';
  }

  async function api(method, path, body) {
    const headers = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (state.token) headers['Authorization'] = 'Bearer ' + state.token;

    let res;
    try {
      res = await fetch(path, {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined
      });
    } catch (e) {
      throw new Error('Cannot reach the server');
    }

    if (res.status === 401 && state.token) {
      logout();
      throw new Error('Session expired. Please log in again.');
    }

    let data = null;
    const text = await res.text();
    if (text) {
      try { data = JSON.parse(text); } catch (e) { data = null; }
    }
    if (!res.ok) throw new Error(errorMessage(data, res.status));
    return data;
  }

  // ------------------------------------------------------------------ auth

  function showAuthError(message) { $('auth-error').textContent = message || ''; }

  function setAuthTab(login) {
    $('tab-login').classList.toggle('active', login);
    $('tab-register').classList.toggle('active', !login);
    $('login-form').hidden = !login;
    $('register-form').hidden = login;
    showAuthError('');
  }

  async function onLogin(event) {
    event.preventDefault();
    showAuthError('');
    const button = event.target.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      const res = await api('POST', '/api/auth/login', {
        username: $('login-username').value.trim(),
        password: $('login-password').value
      });
      startSession(res);
    } catch (err) {
      showAuthError(err.message);
    } finally {
      button.disabled = false;
    }
  }

  async function onRegister(event) {
    event.preventDefault();
    showAuthError('');
    const button = event.target.querySelector('button[type="submit"]');
    button.disabled = true;
    const username = $('register-username').value.trim();
    const password = $('register-password').value;
    try {
      await api('POST', '/api/auth/register', {
        username,
        email: $('register-email').value.trim(),
        password
      });
      const res = await api('POST', '/api/auth/login', { username, password });   // log in straight away
      startSession(res);
    } catch (err) {
      showAuthError(err.message);
    } finally {
      button.disabled = false;
    }
  }

  function startSession(res) {
    state.token = res.token;
    state.me = { id: res.userId, username: res.username };
    sessionStorage.setItem('token', state.token);
    sessionStorage.setItem('me', JSON.stringify(state.me));
    showApp();
  }

  function showAuth() {
    $('app-screen').hidden = true;
    $('auth-screen').hidden = false;
  }

  function showApp() {
    $('auth-screen').hidden = true;
    $('app-screen').hidden = false;
    $('me-name').textContent = state.me.username;
    state.conversations = [];
    state.history = {};
    closeChat();
    renderConversationList();
    loadConversations();
    connectWebSocket();
  }

  function logout() {
    if (state.client) {
      try { state.client.deactivate(); } catch (e) { /* ignore */ }
      state.client = null;
    }
    state.connected = false;
    state.token = null;
    state.me = null;
    state.conversations = [];
    state.history = {};
    state.currentId = null;
    state.leaving.clear();
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('me');
    closeModal();
    $('app-screen').classList.remove('chat-open');
    showAuth();
  }

  // ------------------------------------------------------------------ WebSocket

  function setConnectionStatus(connected) {
    $('me-dot').className = 'dot ' + (connected ? 'online' : 'offline');
    $('me-dot').title = connected ? 'Connected' : 'Disconnected - reconnecting...';
  }

  function connectWebSocket() {
    if (state.client) {
      try { state.client.deactivate(); } catch (e) { /* ignore */ }
    }

    const scheme = location.protocol === 'https:' ? 'wss://' : 'ws://';
    const client = new StompJs.Client({
      brokerURL: scheme + location.host + '/ws',
      connectHeaders: { Authorization: 'Bearer ' + state.token },
      reconnectDelay: 5000,

      onConnect: () => {
        state.connected = true;
        setConnectionStatus(true);

        client.subscribe('/user/queue/messages', (frame) => onIncomingMessage(JSON.parse(frame.body)));
        client.subscribe('/user/queue/receipts', (frame) => onReceipt(JSON.parse(frame.body)));
        client.subscribe('/user/queue/presence', (frame) => onPresence(JSON.parse(frame.body)));
        client.subscribe('/user/queue/conversations', (frame) => onConversationEvent(JSON.parse(frame.body)));
        client.subscribe('/user/queue/errors', (frame) => {
          const data = JSON.parse(frame.body);
          toast(data.error || 'Something went wrong');
        });

        // (re)connected: refresh everything that may have changed while we were away
        const openId = state.currentId;
        state.history = {};
        loadConversations().then(() => {
          if (openId && findConv(openId)) {
            loadHistory(openId, true).then(() => sendRead(openId));
          }
        });
      },

      onStompError: (frame) => {
        const message = (frame.headers && frame.headers['message']) || 'Connection error';
        if (/token|authorization|authenticated/i.test(message)) {
          toast('Session expired. Please log in again.');
          logout();
        } else {
          toast(message);
        }
      },

      onWebSocketClose: () => {
        state.connected = false;
        setConnectionStatus(false);
      }
    });

    state.client = client;
    client.activate();
  }

  function publish(destination, payload) {
    if (!state.connected || !state.client) return false;
    state.client.publish({ destination, body: JSON.stringify(payload) });
    return true;
  }

  function sendRead(conversationId) {
    publish('/app/chat.read', { conversationId });
  }

  // ------------------------------------------------------------------ live event handlers

  async function onIncomingMessage(message) {
    let conv = findConv(message.conversationId);
    const wasKnown = !!conv;

    if (!conv) {   // a chat we have not seen yet (someone just messaged us)
      await loadConversations();
      conv = findConv(message.conversationId);
      if (!conv) return;
    }

    const box = $('messages');
    const nearBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 140;

    const h = state.history[message.conversationId];
    if (h && h.loaded && !h.items.some((m) => m.id === message.id)) h.items.push(message);

    conv.lastMessage = message;

    const mine = message.senderId === state.me.id;
    const isOpen = message.conversationId === state.currentId;
    const visible = document.visibilityState === 'visible';

    if (!mine) {
      if (isOpen && visible) {
        sendRead(message.conversationId);
      } else if (wasKnown) {   // a freshly loaded list already counts this message
        conv.unreadCount = (conv.unreadCount || 0) + 1;
      }
    }

    sortConversations();
    renderConversationList();
    if (isOpen) renderMessages({ scroll: mine || nearBottom ? 'bottom' : 'keep' });
  }

  function onReceipt(event) {
    const conv = findConv(event.conversationId);
    if (!conv) return;
    const member = conv.members.find((m) => m.username === event.username);
    if (!member) return;

    member.lastDeliveredMessageId = maxNum(member.lastDeliveredMessageId, event.lastDeliveredMessageId);
    member.lastReadMessageId = maxNum(member.lastReadMessageId, event.lastReadMessageId);

    // I read this conversation (maybe in another tab)
    if (event.username === state.me.username && conv.lastMessage
        && member.lastReadMessageId >= conv.lastMessage.id && conv.unreadCount) {
      conv.unreadCount = 0;
      renderConversationList();
    }
    if (event.conversationId === state.currentId) renderMessages({ scroll: 'keep' });
  }

  function onPresence(event) {
    let touched = false;
    for (const conv of state.conversations) {
      for (const member of conv.members) {
        if (member.username === event.username) {
          member.online = event.online;
          if (event.lastSeen) member.lastSeen = event.lastSeen;
          touched = true;
        }
      }
    }
    if (!touched) return;
    renderConversationList();
    if (state.currentId) renderChatHeader();
    if (state.modalRefresh) state.modalRefresh();
  }

  async function onConversationEvent(event) {
    if (event.action === 'REMOVED') {
      if (state.leaving.has(event.conversationId)) {
        state.leaving.delete(event.conversationId);
      } else {
        toast('You were removed from a group');
      }
      delete state.history[event.conversationId];
      if (event.conversationId === state.currentId) closeChat();
    }
    await loadConversations();
  }

  function onVisibilityChange() {
    if (document.visibilityState !== 'visible' || !state.currentId) return;
    const conv = findConv(state.currentId);
    if (conv) {
      conv.unreadCount = 0;
      renderConversationList();
    }
    sendRead(state.currentId);
  }

  // ------------------------------------------------------------------ conversations

  function findConv(id) {
    return state.conversations.find((c) => c.id === id);
  }

  function otherMember(conv) {
    return conv.members.find((m) => m.userId !== state.me.id) || null;
  }

  function convTitle(conv) {
    if (conv.type === 'GROUP') return conv.name || 'Group';
    const other = otherMember(conv);
    return other ? other.username : 'Chat';
  }

  function activityTime(conv) {
    const d = parseDate(conv.lastMessage ? conv.lastMessage.createdAt : conv.createdAt);
    return d ? d.getTime() : 0;
  }

  function sortConversations() {
    state.conversations.sort((a, b) => activityTime(b) - activityTime(a));
  }

  async function loadConversations() {
    try {
      state.conversations = await api('GET', '/api/conversations');
      sortConversations();
      renderConversationList();
      if (state.currentId) {
        if (findConv(state.currentId)) renderChatHeader();
        else closeChat();
      }
      if (state.modalRefresh) state.modalRefresh();
    } catch (err) {
      toast(err.message);
    }
  }

  function previewText(conv) {
    const m = conv.lastMessage;
    if (!m) return 'No messages yet';
    if (m.senderId === state.me.id) return 'You: ' + m.content;
    return conv.type === 'GROUP' ? m.senderUsername + ': ' + m.content : m.content;
  }

  function conversationItem(conv) {
    const other = conv.type === 'ONE_TO_ONE' ? otherMember(conv) : null;
    const title = convTitle(conv);

    const avatar = el('div', { class: 'avatar' },
      title.charAt(0).toUpperCase(),
      other && other.online ? el('span', { class: 'dot online' }) : null);

    return el('div', {
      class: 'conv-item' + (conv.id === state.currentId ? ' active' : ''),
      onclick: () => selectConversation(conv.id)
    },
      avatar,
      el('div', { class: 'conv-body' },
        el('div', { class: 'conv-top' },
          el('span', { class: 'conv-name' }, title),
          el('span', { class: 'conv-time' }, conv.lastMessage ? listTime(conv.lastMessage.createdAt) : '')),
        el('div', { class: 'conv-bottom' },
          el('span', { class: 'conv-preview' }, previewText(conv)),
          conv.unreadCount > 0 ? el('span', { class: 'badge' }, String(conv.unreadCount)) : null)));
  }

  function renderConversationList() {
    const list = $('conversation-list');
    list.replaceChildren();
    if (!state.conversations.length) {
      list.append(el('div', { class: 'conv-empty' }, 'No conversations yet. Start a chat or create a group.'));
      return;
    }
    for (const conv of state.conversations) list.append(conversationItem(conv));
  }

  async function selectConversation(id) {
    const conv = findConv(id);
    if (!conv) return;

    state.currentId = id;
    conv.unreadCount = 0;
    $('app-screen').classList.add('chat-open');
    $('empty-state').hidden = true;
    $('chat-view').hidden = false;
    $('messages').replaceChildren();

    renderConversationList();
    renderChatHeader();

    const h = ensureHistory(id);
    if (h.loaded) renderMessages({ scroll: 'bottom' });
    else await loadHistory(id, true);

    if (state.currentId === id) {
      sendRead(id);
      $('message-input').focus();
    }
  }

  function closeChat() {
    state.currentId = null;
    $('chat-view').hidden = true;
    $('empty-state').hidden = false;
    $('app-screen').classList.remove('chat-open');
    renderConversationList();
  }

  function renderChatHeader() {
    const conv = findConv(state.currentId);
    if (!conv) return;

    $('chat-title').textContent = convTitle(conv);

    let subtitle = '';
    if (conv.type === 'GROUP') {
      subtitle = conv.members.map((m) => (m.userId === state.me.id ? 'You' : m.username)).join(', ');
    } else {
      const other = otherMember(conv);
      if (other) subtitle = other.online ? 'online' : lastSeenText(other.lastSeen);
    }
    $('chat-subtitle').textContent = subtitle;
    $('info-btn').hidden = conv.type !== 'GROUP';
  }

  // ------------------------------------------------------------------ messages

  function ensureHistory(id) {
    if (!state.history[id]) {
      state.history[id] = { items: [], page: 0, last: false, loaded: false, loading: false };
    }
    return state.history[id];
  }

  async function loadHistory(id, initial) {
    const h = ensureHistory(id);
    if (h.loading) return;
    h.loading = true;

    const prevHeight = $('messages').scrollHeight;
    try {
      const page = initial ? 0 : h.page + 1;
      const res = await api('GET', '/api/conversations/' + id + '/messages?page=' + page + '&size=' + PAGE_SIZE);
      const batch = res.content.slice().reverse();   // server sends newest first

      if (initial) {
        h.items = batch;
      } else {
        const known = new Set(h.items.map((m) => m.id));   // new messages can shift pages, so skip duplicates
        h.items = batch.filter((m) => !known.has(m.id)).concat(h.items);
      }
      h.page = page;
      h.last = res.last;
      h.loaded = true;
    } catch (err) {
      toast(err.message);
    } finally {
      h.loading = false;
    }

    if (id === state.currentId) {
      renderMessages(initial ? { scroll: 'bottom' } : { keepFrom: prevHeight });
    }
  }

  // sent -> delivered -> read, worked out from the other members' positions
  function messageStatus(conv, message) {
    const others = conv.members.filter((m) => m.userId !== message.senderId);
    if (!others.length) return 'sent';
    if (others.every((m) => (m.lastReadMessageId || 0) >= message.id)) return 'read';
    if (others.every((m) => (m.lastDeliveredMessageId || 0) >= message.id)) return 'delivered';
    return 'sent';
  }

  function messageRow(message, conv) {
    const mine = message.senderId === state.me.id;
    let ticks = null;
    if (mine) {
      const status = messageStatus(conv, message);
      ticks = el('span', {
        class: 'ticks' + (status === 'read' ? ' read' : ''),
        title: status
      }, status === 'sent' ? '\u2713' : '\u2713\u2713');
    }

    return el('div', { class: 'msg-row ' + (mine ? 'mine' : 'theirs') },
      el('div', { class: 'bubble' },
        !mine && conv.type === 'GROUP' ? el('div', { class: 'sender' }, message.senderUsername) : null,
        el('div', { class: 'text' }, message.content),
        el('div', { class: 'meta' }, formatTime(message.createdAt), ticks)));
  }

  // opts: { scroll: 'bottom' | 'keep' } or { keepFrom: previousScrollHeight } after loading older messages
  function renderMessages(opts) {
    const box = $('messages');
    const conv = findConv(state.currentId);
    const h = state.history[state.currentId];
    if (!conv || !h) return;

    const previousTop = box.scrollTop;
    box.replaceChildren();

    if (!h.last && h.items.length) {
      box.append(el('button', {
        class: 'small ghost load-older',
        type: 'button',
        onclick: () => loadHistory(state.currentId, false)
      }, 'Load older messages'));
    }
    if (h.loaded && !h.items.length) {
      box.append(el('div', { class: 'day-label' }, 'No messages yet. Say hello!'));
    }

    let lastDay = null;
    for (const message of h.items) {
      const d = parseDate(message.createdAt);
      if (d) {
        const key = startOfDay(d).getTime();
        if (key !== lastDay) {
          box.append(el('div', { class: 'day-label' }, dayLabel(d)));
          lastDay = key;
        }
      }
      box.append(messageRow(message, conv));
    }

    if (opts && opts.keepFrom !== undefined) box.scrollTop = box.scrollHeight - opts.keepFrom;
    else if (opts && opts.scroll === 'bottom') box.scrollTop = box.scrollHeight;
    else box.scrollTop = previousTop;
  }

  function onMessagesScroll() {
    const box = $('messages');
    if (box.scrollTop > 30 || !state.currentId) return;
    const h = state.history[state.currentId];
    if (h && h.loaded && !h.last && !h.loading) loadHistory(state.currentId, false);
  }

  function onSend(event) {
    event.preventDefault();
    const input = $('message-input');
    const text = input.value.trim();
    if (!text || !state.currentId) return;

    const sent = publish('/app/chat.send', { conversationId: state.currentId, content: text });
    if (!sent) {
      toast('Not connected. Reconnecting...');
      return;
    }
    input.value = '';
  }

  // ------------------------------------------------------------------ modals

  function openModal(title, build) {
    closeModal();
    const overlay = el('div', {
      class: 'modal-overlay',
      onclick: (event) => { if (event.target === overlay) closeModal(); }
    });
    const body = el('div', { class: 'modal-body' });
    const box = el('div', { class: 'modal', role: 'dialog' },
      el('div', { class: 'modal-header' },
        el('h3', null, title),
        el('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Close', onclick: closeModal }, '\u00d7')),
      body);
    overlay.append(box);
    $('modal-root').append(overlay);
    build(body);
  }

  function closeModal() {
    $('modal-root').replaceChildren();
    state.modalRefresh = null;
  }

  function userRow(user, onClick) {
    return el('div', { class: 'user-row clickable', onclick: onClick },
      el('span', { class: 'dot ' + (user.online ? 'online' : 'offline') }),
      el('div', { class: 'grow' },
        el('div', { class: 'name' }, user.username),
        el('div', { class: 'sub' }, user.online ? 'online' : lastSeenText(user.lastSeen))));
  }

  // search box + result list; calls onPick(user) when a result is clicked
  function createUserSearch(onPick, excludedNames) {
    const input = el('input', { type: 'text', placeholder: 'Search users by name' });
    const results = el('div', { class: 'user-results' });
    let timer = null;
    let sequence = 0;

    async function run() {
      const mine = ++sequence;
      try {
        const users = await api('GET', '/api/users?search=' + encodeURIComponent(input.value.trim()));
        if (mine !== sequence) return;   // a newer search is already running
        const excluded = excludedNames();
        const shown = users.filter((u) => !excluded.includes(u.username));
        results.replaceChildren();
        if (!shown.length) results.append(el('div', { class: 'user-row' }, el('span', { class: 'hint' }, 'No users found')));
        for (const user of shown) results.append(userRow(user, () => onPick(user)));
      } catch (err) {
        toast(err.message);
      }
    }

    input.addEventListener('input', () => {
      clearTimeout(timer);
      timer = setTimeout(run, 250);
    });
    run();
    return { input, results, refresh: run };
  }

  function openNewChatModal() {
    openModal('New chat', (body) => {
      body.append(el('p', { class: 'hint' }, 'Pick someone to chat with.'));
      const search = createUserSearch(async (user) => {
        try {
          const conv = await api('POST', '/api/conversations', { username: user.username });
          closeModal();
          await loadConversations();
          selectConversation(conv.id);
        } catch (err) {
          toast(err.message);
        }
      }, () => []);
      body.append(search.input, search.results);
      search.input.focus();
    });
  }

  function openNewGroupModal() {
    openModal('New group', (body) => {
      const selected = [];
      const nameInput = el('input', { type: 'text', placeholder: 'Group name', maxlength: '100' });
      const chips = el('div', { class: 'chips' });
      const createButton = el('button', { class: 'primary', type: 'button' }, 'Create group');

      const search = createUserSearch((user) => {
        if (!selected.includes(user.username)) selected.push(user.username);
        renderChips();
        search.refresh();
      }, () => selected);

      function renderChips() {
        chips.replaceChildren();
        for (const name of selected) {
          chips.append(el('span', {
            class: 'chip',
            title: 'Click to remove',
            onclick: () => {
              selected.splice(selected.indexOf(name), 1);
              renderChips();
              search.refresh();
            }
          }, name + ' \u00d7'));
        }
      }

      createButton.addEventListener('click', async () => {
        if (!nameInput.value.trim()) { toast('Enter a group name'); return; }
        if (!selected.length) { toast('Add at least one member'); return; }
        createButton.disabled = true;
        try {
          const conv = await api('POST', '/api/conversations/group', {
            name: nameInput.value.trim(),
            usernames: selected
          });
          closeModal();
          await loadConversations();
          selectConversation(conv.id);
        } catch (err) {
          toast(err.message);
          createButton.disabled = false;
        }
      });

      body.append(
        nameInput,
        el('p', { class: 'hint' }, 'Add members (click a name to add, click a chip to remove):'),
        chips, search.input, search.results, createButton);
      nameInput.focus();
    });
  }

  async function removeMember(conversationId, username) {
    try {
      await api('DELETE', '/api/conversations/' + conversationId + '/members/' + encodeURIComponent(username));
      await loadConversations();
    } catch (err) {
      toast(err.message);
    }
  }

  async function leaveGroup(conv) {
    if (!window.confirm('Leave "' + convTitle(conv) + '"?')) return;
    state.leaving.add(conv.id);
    try {
      await api('DELETE', '/api/conversations/' + conv.id + '/members/' + encodeURIComponent(state.me.username));
      closeModal();
      delete state.history[conv.id];
      if (state.currentId === conv.id) closeChat();
      await loadConversations();
    } catch (err) {
      state.leaving.delete(conv.id);
      toast(err.message);
    }
  }

  function openGroupInfo() {
    const first = findConv(state.currentId);
    if (!first || first.type !== 'GROUP') return;
    const conversationId = first.id;

    openModal('Group info', (body) => {
      function render() {
        const conv = findConv(conversationId);
        if (!conv) { closeModal(); return; }

        const iAmAdmin = conv.createdBy === state.me.username;
        body.replaceChildren();
        body.append(
          el('div', { class: 'section-title' }, conv.name || 'Group'),
          el('p', { class: 'hint' }, 'Created by ' + (conv.createdBy || 'unknown') + ' \u00b7 ' + conv.members.length + ' members'));

        const list = el('div', { class: 'user-results' });
        for (const member of conv.members) {
          const isMe = member.userId === state.me.id;
          list.append(el('div', { class: 'user-row' },
            el('span', { class: 'dot ' + (member.online ? 'online' : 'offline') }),
            el('div', { class: 'grow' },
              el('div', { class: 'name' }, isMe ? member.username + ' (you)' : member.username),
              el('div', { class: 'sub' }, member.online ? 'online' : lastSeenText(member.lastSeen))),
            member.username === conv.createdBy ? el('span', { class: 'tag' }, 'admin') : null,
            iAmAdmin && !isMe
              ? el('button', {
                  class: 'small danger',
                  type: 'button',
                  onclick: () => removeMember(conv.id, member.username)
                }, 'Remove')
              : null));
        }
        body.append(list);

        if (iAmAdmin) {
          body.append(el('div', { class: 'section-title' }, 'Add a member'));
          const search = createUserSearch(async (user) => {
            try {
              await api('POST', '/api/conversations/' + conversationId + '/members', { username: user.username });
              await loadConversations();
            } catch (err) {
              toast(err.message);
            }
          }, () => conv.members.map((m) => m.username));
          body.append(search.input, search.results);
        }

        body.append(el('button', {
          class: 'small danger',
          type: 'button',
          onclick: () => leaveGroup(conv)
        }, 'Leave group'));
      }

      state.modalRefresh = render;
      render();
    });
  }

  // ------------------------------------------------------------------ start

  function init() {
    $('tab-login').addEventListener('click', () => setAuthTab(true));
    $('tab-register').addEventListener('click', () => setAuthTab(false));
    $('login-form').addEventListener('submit', onLogin);
    $('register-form').addEventListener('submit', onRegister);

    $('logout-btn').addEventListener('click', logout);
    $('new-chat-btn').addEventListener('click', openNewChatModal);
    $('new-group-btn').addEventListener('click', openNewGroupModal);
    $('info-btn').addEventListener('click', openGroupInfo);
    $('back-btn').addEventListener('click', closeChat);
    $('composer').addEventListener('submit', onSend);
    $('messages').addEventListener('scroll', onMessagesScroll);

    document.addEventListener('keydown', (event) => { if (event.key === 'Escape') closeModal(); });
    document.addEventListener('visibilitychange', onVisibilityChange);

    if (state.token && state.me) showApp();
    else showAuth();
  }

  init();
})();
