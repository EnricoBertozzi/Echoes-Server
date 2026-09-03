(function(){
  const validateLoading = document.getElementById('validate-loading');
  const changeLoading = document.getElementById('change-loading');
  const disablingLoading = document.getElementById('disabling-loading');
  const deletingLoading = document.getElementById('deleting-loading');
  let loading = false;
  let disableLoading = false;
  let deleteLoading = false;

  /**
   * Atualiza o estado de carregamento da interface.
   * Exibe ou oculta os indicadores visuais de loading.
   *
   * @param {boolean} newLoading Novo estado de carregamento
   */
  function setLoading(newLoading) {
    loading = newLoading;
    if (loading) {
      validateLoading.classList.remove('hidden');
      changeLoading.classList.remove('hidden');
    } else {
      validateLoading.classList.add('hidden');
      changeLoading.classList.add('hidden');
    }
  }

  function setDisableLoading(newLoading) {
    disableLoading = newLoading;
    if (disableLoading) {
      disablingLoading.classList.remove('hidden');
    } else {
      disablingLoading.classList.add('hidden');
    }
  }

  function setDeleteLoading(newLoading) {
    deleteLoading = newLoading;
    if (deleteLoading) {
      deletingLoading.classList.remove('hidden');
      deleteMfaLoading.classList.remove('hidden');
    } else {
      deletingLoading.classList.add('hidden');
      deleteMfaLoading.classList.add('hidden');
    }
  }

  const newPasswordSection = document.getElementById('newPasswordSection');
  const deleteSection = document.getElementById('delete-section');
  const deleteMfaSection = document.getElementById('delete-mfa-section');
  const deleteMfaLoading = document.getElementById('delete-mfa-loading');

  let resetToken = '';

  /**
   * Valida a senha atual do usuário antes de permitir a troca.
   * Retorna um token temporário caso a validação seja bem-sucedida.
   *
   * @param {string} password Senha atual informada pelo usuário
   * @returns {Promise<{ok: boolean, message: string}>}
   */
  async function validatePassword(password) {
    const res = await fetch('/api/password/validate', {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ password }),
    });

    let message = '';

    if (!res.ok) {
      if (res.status === 429) {
        const retryAfter = res.headers.get('Retry-After');
        message = `Muitas requisições! Tente novamente em ${retryAfter || 'alguns'} segundos.`;
      } else {
        message = 'Senha incorreta';
      }

      return {
        ok: false,
        message,
      };
    }

    const data = await res.json();
    resetToken = data.token;

    return {
      ok: true,
      message,
    };
  }

  /**
   * Envia a nova senha para a API utilizando o token temporário.
   *
   * @param {string} password Nova senha do usuário
   * @returns {Promise<{ok: boolean, message: string}>}
   */
  async function changePassword(password) {
    const res = await fetch('/api/password/change', {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        token: resetToken,
        newPassword: password,
        confirmPassword: password,
      }),
    });

    let message = '';

    if (!res.ok) {
      if (res.status === 429) {
        const retryAfter = res.headers.get('Retry-After');
        message = `Muitas requisições! Tente novamente em ${retryAfter || 'alguns'} segundos.`;
      } else {
        message = 'Um erro ocorreu';
      }
    }

    return {
      ok: res.ok,
      message,
    };
  }

  async function disableAccount() {
    const res = await fetch('/api/terms/revoke', {
      method: 'POST',
    });
    return {
      ok: res.ok,
      message: res.ok ? '' : 'Um erro ocorreu,'
    };
  }

  async function requestDeleteAccount() {
    const res = await fetch('/api/users/me/delete/request', {
      method: 'POST',
    })
    return res.ok;
  }

  async function confirmDeleteAccount(code) {
    const res = await fetch('/api/users/me/delete', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code }),
    })
    return {
      ok: res.ok,
      message: res.ok ? '' : 'Um erro ocorreu',
    }
  }

  /**
   * Evento disparado ao clicar no botão de validação.
   * Verifica a senha atual e libera o formulário de nova senha.
   */
  document.getElementById('validate-btn').addEventListener('click', async () => {
    try {
      if (loading)
        return;

      setLoading(true);

      const current = document.getElementById('currentPassword').value;
      const res = await validatePassword(current);

      if (!res.ok)
        return alert(res.message);

      newPasswordSection.classList.remove("hidden");
      startTokenWatcher(resetToken);
    } catch(err) {
      console.error(err);
      alert('Um erro ocorreu');
    } finally {
      setLoading(false);
    }
  });

  document.getElementById('disable-btn').addEventListener('click', async () => {
    try {
      if (disableLoading)
        return;

      setDisableLoading(true);
      const shouldDisable = prompt('Tem certeza que deseja desativar sua conta? Digite SIM');
      if (shouldDisable !== 'SIM')
        return;

      const res = await disableAccount();
      if (res.ok) {
        window.location.href = '/';
      } else {
        alert(err.message);
      }
    } catch(err) {
      console.error(err);
      alert('Um erro ocorreu ao desativar a conta');
    } finally {
      setDisableLoading(false);
    }
  })

  document.getElementById('delete-btn').addEventListener('click', async () => {
    try {
      if (deleteLoading)
        return;

      setDeleteLoading(true);
      const shouldDelete = prompt('Tem certeza que quer deletar sua conta? Digite SIM');
      if (shouldDelete !== 'SIM')
        return;

      const ok = await requestDeleteAccount();
      if (!ok) {
        alert('Um erro ocorreu ao solicitar o código');
        return;
      }

      deleteSection.classList.add('hidden');
      deleteMfaSection.classList.remove('hidden');
    } catch(err) {
      console.error(err);
      alert('Um erro ocorreu');
    } finally {
      setDeleteLoading(false);
    }
  })

  document.getElementById('delete-confirm-btn').addEventListener('click', async () => {
    try {
      if (deleteLoading)
        return;

      const code = document.getElementById('delete-code').value;
      if (!code) {
        alert('Digite o código de verificação');
        return;
      }

      setDeleteLoading(true);

      const res = await confirmDeleteAccount(code);
      if (res.ok) {
        window.location.href = '/';
      } else {
        alert(res.message);
      }
    } catch(err) {
      console.error(err);
      alert('Um erro ocorreu');
    } finally {
      setDeleteLoading(false);
    }
  })

  /**
   * Evento disparado ao enviar o formulário de nova senha.
   * Verifica se as senhas coincidem e envia a alteração para a API.
   */
  newPasswordSection.addEventListener('submit', async evt => {
    try {
      evt.preventDefault();

      if (loading)
        return;

      setLoading(true);

      const newPass = document.getElementById('newPassword').value;
      const confirmPass = document.getElementById('confirmPassword').value;

      if (newPass !== confirmPass)
        return alert('As senhas não são equivalentes');

      const res = await changePassword(newPass);

      if (!res.ok)
        alert(res.message);
    } catch(err) {
      console.error(err);
      alert('Um erro ocorreu');
    } finally {
      setLoading(false);
      window.location.reload();
    }
  });



  /**
   * Inicia um temporizador baseado na expiração do token.
   * Quando expirar, remove o token e oculta o formulário.
   *
   * @param {string} token Token JWT temporário
   */
  function startTokenWatcher(token) {
    const decoded = parseJwt(token);
    const now = Math.floor(Date.now() / 1000);

    const timeLeft = (decoded.exp - now) * 1000;

    setTimeout(() => {
      resetToken = null;
      newPasswordSection.classList.add("hidden");

      alert("Sessão expirada. Valide sua senha novamente.");
    }, timeLeft);
  }

  /**
   * Decodifica um token JWT e retorna seu conteúdo em objeto.
   *
   * @param {string} token Token JWT
   * @returns {object}
   */
  function parseJwt(token) {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');

    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );

    return JSON.parse(jsonPayload);
  }
})();
