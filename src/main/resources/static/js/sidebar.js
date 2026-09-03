(function(){
  const logoutBtn = document.getElementById('logout-btn');

  /**
   * Envia uma requisição para encerrar a sessão do usuário.
   *
   * @returns {Promise<boolean>} Retorna true se o logout for bem-sucedido
   */
  async function logout() {
    const res = await fetch('/api/auth/logout');
    return res.ok;
  }

  /**
   * Evento disparado ao clicar no botão de logout.
   * Encerra a sessão e redireciona o usuário para a página inicial.
   */
  logoutBtn.addEventListener('click', async evt => {
    try {
      evt.preventDefault();

      const res = await logout();

      if (res)
        window.location.href = '/';
      else
        alert('Um erro ocorreu');
    } catch(err){
      console.error(err);
      alert('Um erro ocorreu');
    }
  });
})();
