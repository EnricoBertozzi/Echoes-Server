(function(){
  const registerForm = document.getElementById('registerForm');
  const mfaForm = document.getElementById('mfaForm');
  let emailCache = '';

  const registerLoading = document.getElementById('register-loading');
  const mfaLoading = document.getElementById('mfa-loading');
  let loading = false;

  const password = document.getElementById("password");
  const confirmPassword = document.getElementById("confirmPassword");

  const acceptTerms = document.getElementById('accept-terms');

  /**
   * Verifica se os campos de senha e confirmação são iguais.
   * Caso sejam diferentes, define uma mensagem de erro personalizada.
   */
  function validatePassword() {
    if (password.value !== confirmPassword.value) {
      confirmPassword.setCustomValidity("As senhas não coincidem");
    } else {
      confirmPassword.setCustomValidity("");
    }
  }

  // Valida sempre que o usuário alterar qualquer um dos campos de senha
  password.onchange = validatePassword;
  confirmPassword.onkeyup = validatePassword;

  /**
   * Atualiza o estado de carregamento da interface.
   * Exibe ou oculta os indicadores visuais de loading.
   *
   * @param {boolean} newLoading Novo estado de carregamento
   */
  function setLoading(newLoading) {
    loading = newLoading;
    if (loading) {
      registerLoading.classList.remove('hidden');
      mfaLoading.classList.remove('hidden');
    } else {
      registerLoading.classList.add('hidden');
      mfaLoading.classList.add('hidden');
    }
  }

  /**
   * Evento disparado ao enviar o formulário de cadastro.
   * Envia os dados para a API e, em caso de sucesso,
   * exibe a etapa de verificação por código.
   */
  registerForm.addEventListener('submit', async evt => {
    try {
      evt.preventDefault();
      if (!acceptTerms.checked)
        return alert('Você deve aceitar os termos');

      if (loading)
        return;

      setLoading(true);

      const data = {
        name: document.getElementById("name").value,
        email: document.getElementById("email").value,
        password: document.getElementById("password").value,
        confirmPassword: document.getElementById("confirmPassword").value
      };

      const res = await fetch("/api/auth/register", {
        method: "POST",
        headers: {
          "Content-Type":"application/json"
        },
        body: JSON.stringify(data)
      });

      if (res.ok) {
        emailCache = data.email;

        registerForm.classList.add('hidden');
        mfaForm.classList.remove('hidden');
      } else {
        if (res.status === 429) {
          const retryAfter = res.headers.get('Retry-After');
          alert(`Muitas requisições! Tente novamente em ${retryAfter || 'alguns'} segundos.`);
        } else {
          alert('Erro ao registrar');
        }
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
   * Valida o código enviado ao email e finaliza o cadastro.
   */
  mfaForm.addEventListener('submit', async evt => {
    try {
      evt.preventDefault();

      if (loading)
        return;

      setLoading(true);

      const code = document.getElementById("code").value;

      const res = await fetch("/api/auth/register/2fa", {
        method:"POST",
        headers:{
          "Content-Type":"application/json"
        },
        body: JSON.stringify({
          email: emailCache,
          code: code
        })
      });

      if (res.ok)
        window.location.href = '/';
      else
        if (res.status === 429) {
          const retryAfter = res.headers.get('Retry-After');
          alert(`Muitas requisições! Tente novamente em ${retryAfter || 'alguns'} segundos.`);
        } else {
          alert('Código inválido');
        }
    } catch(err) {
      console.error(err);
      alert('Um erro ocorreu');
    } finally {
      setLoading(false);
    }
  });
})();
