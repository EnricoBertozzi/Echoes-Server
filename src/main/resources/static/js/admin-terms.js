(function(){
  let currentPage = 0;
  let currentSize = 20;
  let loading = false;

  const tbody = document.getElementById('terms-tbody');
  const pagination = document.getElementById('terms-pagination');
  const preview = document.getElementById('terms-preview');
  const formLoading = document.getElementById('terms-form-loading');

  async function loadTerms(page) {
    tbody.innerHTML = '<tr><td colspan="5" class="px-4 py-8 text-center text-gray-500">Carregando...</td></tr>';

    try {
      const res = await fetch(`/api/admin/terms?page=${page}&size=${currentSize}&sort=timestamp,desc`);
      if (!res.ok) {
        tbody.innerHTML = '<tr><td colspan="5" class="px-4 py-8 text-center text-red-500">Erro ao carregar termos</td></tr>';
        return;
      }

      const data = await res.json();
      renderTable(data);
      renderPagination(data);
      currentPage = data.number;
    } catch(err) {
      console.error(err);
      tbody.innerHTML = '<tr><td colspan="5" class="px-4 py-8 text-center text-red-500">Erro ao carregar termos</td></tr>';
    }
  }

  function renderTable(page) {
    if (page.content.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" class="px-4 py-8 text-center text-gray-500">Nenhum termo encontrado</td></tr>';
      return;
    }

    tbody.innerHTML = page.content.map(term => {
      const activeLabel = term.active
        ? '<span class="text-green-600 font-semibold">Sim</span>'
        : '<span class="text-gray-400">Não</span>';
      const date = new Date(term.timestamp);
      const formatted = date.toLocaleString('pt-BR');
      const jsTerm = JSON.stringify(term).replace(/'/g, "&#39;");
      return `<tr class="border-b hover:bg-gray-50">
        <td class="px-4 py-3">${term.type}</td>
        <td class="px-4 py-3">${term.version}</td>
        <td class="px-4 py-3">${activeLabel}</td>
        <td class="px-4 py-3 text-xs">${formatted}</td>
        <td class="px-4 py-3">
          <button class="edit-btn text-primary hover:underline text-sm"
                  data-term='${jsTerm}'>
            Editar
          </button>
        </td>
      </tr>`;
    }).join('');

    tbody.querySelectorAll('.edit-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const term = JSON.parse(btn.dataset.term);
        populateForm(term);
      });
    });
  }

  function renderPagination(page) {
    if (page.totalPages <= 1) {
      pagination.innerHTML = '';
      return;
    }

    const info = document.createElement('div');
    info.className = 'text-sm text-gray-600';
    info.textContent = `Página ${page.number + 1} de ${page.totalPages} (${page.totalElements} registros)`;

    const btns = document.createElement('div');
    btns.className = 'flex gap-2';

    const prev = document.createElement('button');
    prev.className = page.first
      ? 'px-4 py-2 bg-gray-200 text-gray-400 rounded text-sm cursor-not-allowed'
      : 'px-4 py-2 bg-primary text-white rounded hover:bg-blue-900 transition text-sm';
    prev.textContent = 'Anterior';
    if (!page.first) {
      prev.addEventListener('click', () => loadTerms(page.number - 1));
    }

    const next = document.createElement('button');
    next.className = page.last
      ? 'px-4 py-2 bg-gray-200 text-gray-400 rounded text-sm cursor-not-allowed'
      : 'px-4 py-2 bg-primary text-white rounded hover:bg-blue-900 transition text-sm';
    next.textContent = 'Próximo';
    if (!page.last) {
      next.addEventListener('click', () => loadTerms(page.number + 1));
    }

    btns.appendChild(prev);
    btns.appendChild(next);

    pagination.innerHTML = '';
    pagination.appendChild(info);
    pagination.appendChild(btns);
  }

  function populateForm(term) {
    document.getElementById('terms-type').value = term.type;
    document.getElementById('terms-version').value = term.version;
    document.getElementById('terms-content').value = term.content;
    preview.classList.add('hidden');
    window.scrollTo({ top: document.querySelector('.bg-white.shadow.rounded-lg.p-6.mb-6').offsetTop, behavior: 'smooth' });
  }

  document.getElementById('terms-preview-btn').addEventListener('click', () => {
    const content = document.getElementById('terms-content').value;
    if (!content.trim()) {
      alert('Digite o conteúdo HTML primeiro');
      return;
    }
    preview.innerHTML = content;
    preview.classList.remove('hidden');
  });

  document.getElementById('terms-save-btn').addEventListener('click', async () => {
    if (loading) return;

    const type = document.getElementById('terms-type').value;
    const version = document.getElementById('terms-version').value.trim();
    const content = document.getElementById('terms-content').value.trim();

    if (!version) {
      alert('Informe a nova versão');
      return;
    }
    if (!content) {
      alert('Informe o conteúdo');
      return;
    }

    loading = true;
    formLoading.classList.remove('hidden');

    try {
      const res = await fetch('/api/admin/terms', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ version, content, type }),
      });

      if (res.ok) {
        alert('Nova versão criada com sucesso!');
        document.getElementById('terms-version').value = '';
        document.getElementById('terms-content').value = '';
        preview.classList.add('hidden');
        loadTerms(0);
      } else {
        const errMsg = res.status === 400 ? 'Dados inválidos' : 'Erro ao salvar';
        alert(errMsg);
      }
    } catch(err) {
      console.error(err);
      alert('Erro ao salvar');
    } finally {
      loading = false;
      formLoading.classList.add('hidden');
    }
  });

  loadTerms(0);
})();
