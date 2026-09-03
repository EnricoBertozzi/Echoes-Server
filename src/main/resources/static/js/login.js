(function(){
  const loginBtn = document.querySelector('#login-form > button[type=submit]');
  const loginForm = document.getElementById("login-form");
  const mfaForm = document.getElementById("mfa-form");
  const loginLoading = document.getElementById('login-loading');
  const mfaLoading = document.getElementById('mfa-loading');
  let loading = false;
  let userEmail;

  /**
   * Atualiza o estado de carregamento da interface.
   * Exibe ou oculta os indicadores visuais de loading.
   *
   * @param {boolean} newLoading Novo estado de carregamento
   */
  function setLoading(newLoading) {
    loading = newLoading;
    if (loading) {
      loginLoading.classList.remove('hidden');
      mfaLoading.classList.remove('hidden');
    } else {
      loginLoading.classList.add('hidden');
      mfaLoading.classList.add('hidden');
    }
  }

  /**
   * Envia email e senha para a API de login.
   * Retorna o status da requisição e uma mensagem amigável em caso de erro.
   *
   * @param {string} email Email do usuário
   * @param {string} password Senha do usuário
   * @returns {Promise<{ok: boolean, message: string}>}
   */
  async function login(email, password) {
    const res = await fetch("/api/auth/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ email, password })
    });

    let message = '';

    if (!res.ok) {
      if (res.status === 429) {
        const retryAfter = res.headers.get('Retry-After');
        message = `Muitas requisições! Tente novamente em ${retryAfter || 'alguns'} segundos.`;
      } else if (res.status === 400) {
        const data = await res.json();
        if (data.message === 'User is disabled') {
          message = 'Usuário desativado, clique em \'Reative sua conta\'';
        } else {
          message = 'Credenciais inválidas';
        }
      } else {
        message = 'Credenciais inválidas';
      }
    }

    return {
      ok: res.ok,
      message,
    };
  }

  /**
   * Envia o código de autenticação em dois fatores para validação.
   * Retorna o status da requisição e uma mensagem amigável em caso de erro.
   *
   * @param {string} email Email do usuário autenticado
   * @param {string} code Código MFA informado pelo usuário
   * @returns {Promise<{ok: boolean, message: string}>}
   */
  async function verifyCode(email, code) {
    const res = await fetch("/api/auth/login/2fa", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        email,
        code,
      })
    });

    let message = '';

    if (!res.ok) {
      if (res.status === 429) {
        const retryAfter = res.headers.get('Retry-After');
        message = `Muitas requisições! Tente novamente em ${retryAfter || 'alguns'} segundos.`;
      } else {
        message = 'Código inválido';
      }
    }

    return {
      ok: res.ok,
      message,
    };
  }

  /**
   * Evento disparado ao clicar no botão de login.
   * Faz a autenticação inicial e, se bem-sucedida,
   * exibe o formulário de MFA.
   */
  loginBtn.addEventListener('click', async evt => {
    try {
      evt.preventDefault();

      if (loading)
        return;

      setLoading(true);

      const email = document.getElementById("email").value;
      const password = document.getElementById("password").value;
      const res = await login(email, password);

      if (res.ok) {
        userEmail = email;
        loginForm.classList.add("hidden");
        mfaForm.classList.remove("hidden");
      } else {
        alert(res.message);
      }
    } catch(err) {
      console.error(err);
      alert('Um erro ocorreu');
    } finally {
      setLoading(false);
    }
  });

  /**
   * Evento disparado ao enviar o formulário de MFA.
   * Valida o código informado e redireciona o usuário
   * para o dashboard em caso de sucesso.
   */
  mfaForm.addEventListener('submit', async evt => {
    try {
      evt.preventDefault();

      if (loading)
        return;

      setLoading(true);

      const code = document.getElementById("code").value;
      const res = await verifyCode(userEmail, code);

      if (res.ok)
        window.location.href = '/dashboard';
      else
        alert(res.message);
    } catch(err) {
      console.error(err);
      alert('Um erro ocorreu');
    } finally {
      setLoading(false);
    }
  });
})();
