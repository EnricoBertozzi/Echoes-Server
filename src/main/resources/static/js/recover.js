(function(){
  const recoverForm = document.getElementById("recover-form");
  const resetForm = document.getElementById("reset-form");

  const recoverBtn = document.querySelector('#recover-form > button');

  let userEmail;

  const recoverLoading = document.getElementById('recover-loading');
  const resetLoading = document.getElementById('reset-loading');
  let loading = false;

  /**
   * Atualiza o estado de carregamento da interface.
   * Exibe ou oculta os indicadores visuais de loading.
   *
   * @param {boolean} newLoading Novo estado de carregamento
   */
  function setLoading(newLoading) {
    loading = newLoading;
    if (loading) {
      recoverLoading.classList.remove('hidden');
      resetLoading.classList.remove('hidden');
    } else {
      recoverLoading.classList.add('hidden');
      resetLoading.classList.add('hidden');
    }
  }

  /**
   * Solicita o envio do código de recuperação para o email informado.
   *
   * @param {string} email Email do usuário
   * @returns {Promise<{ok: boolean, message: string}>}
   */
  async function sendCode(email) {
    const res = await fetch("/api/password/forgot", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ email })
    });

    let message = '';

    if (!res.ok) {
      if (res.status === 429) {
        const retryAfter = res.headers.get('Retry-After');
        message = `Muitas requisições! Tente novamente em ${retryAfter || 'alguns'} segundos.`;
      } else {
        message = 'Erro ao enviar código';
      }
    }

    return {
      ok: res.ok,
      message,
    };
  }

  /**
   * Envia o código de recuperação e a nova senha para redefinir a senha do usuário.
   *
   * @param {string} email Email do usuário
   * @param {string} code Código de recuperação
   * @param {string} password Nova senha
   * @returns {Promise<{ok: boolean, message: string}>}
   */
  async function resetPassword(email, code, password) {
    const res = await fetch("/api/password/reset", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        email,
        code,
        newPassword: password,
        confirmPassword: password,
      })
    });

    let message = '';

    if (!res.ok) {
      if (res.status === 429) {
        const retryAfter = res.headers.get('Retry-After');
        message = `Muitas requisições! Tente novamente em ${retryAfter || 'alguns'} segundos.`;
      } else {
        message = 'Código inválido ou expirado';
      }
    }

    return {
      ok: res.ok,
      message,
    };
  }

  /**
   * Evento disparado ao clicar no botão de recuperação.
   * Solicita o envio do código e exibe o formulário de redefinição.
   */
  recoverBtn.addEventListener('click', async evt => {
    try {
      evt.preventDefault();

      if (loading)
        return;

      setLoading(true);

      const email = document.getElementById("email").value;
      const res = await sendCode(email);

      if (res.ok) {
        userEmail = email;
        recoverForm.classList.add("hidden");
        resetForm.classList.remove("hidden");
      } else {
        alert(res.message);
      }

    } catch (err) {
      console.error(err);
      alert("Um erro ocorreu");
    } finally {
      setLoading(false);
    }
  });

  /**
   * Evento disparado ao enviar o formulário de redefinição.
   * Valida as senhas e tenta concluir a troca da senha.
   */
  resetForm.addEventListener('submit', async evt => {
    try {
      evt.preventDefault();

      if (loading)
        return;

      setLoading(true);

      const code = document.getElementById("code").value;
      const password = document.getElementById("new-password").value;
      const confirm = document.getElementById("confirm-password").value;

      if (password !== confirm)
        return alert("As senhas devem ser equivalentes");

      const res = await resetPassword(userEmail, code, password);

      if (res.ok) {
        alert("Senha redefinida com sucesso");
        window.location.href = "/";
      } else {
        alert(res.message);
      }

    } catch (err) {
      console.error(err);
      alert("Um erro ocorreu");
    } finally {
      setLoading(false);
    }
  });

})();
