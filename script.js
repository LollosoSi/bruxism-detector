const appRoot = document.getElementById('app-root');

// Funzione per caricare le sezioni dinamicamente
async function loadSection(sectionName) {
  try {
    // Aggiunge un effetto di fade out
    appRoot.style.opacity = '0';
    
    // Attende un attimo per l'animazione, poi scarica il file
    setTimeout(async () => {
      const response = await fetch(`sections/${sectionName}.html`, { cache: 'no-store' });
      
      if (!response.ok) {
        appRoot.innerHTML = `<div class="card"><h2>Error 404</h2><p>Page "${sectionName}" not found.</p><button class="back-btn" onclick="loadSection('home')">← Back to Home</button></div>`;
      } else {
        const html = await response.text();
        appRoot.innerHTML = html;
      }
      
      // Scrolla in cima e fa il fade in
      window.scrollTo({ top: 0, behavior: 'smooth' });
      appRoot.style.opacity = '1';
    }, 200); // 200ms corrisponde al tempo della transizione CSS

  } catch (error) {
    console.error("Error loading section:", error);
  }
}

// Carica la Home di default quando si apre il sito
document.addEventListener('DOMContentLoaded', () => {
  // Imposta la transizione per il fade
  appRoot.style.transition = 'opacity 0.2s ease-in-out';
  loadSection('home');
});

// Header scroll
let lastScrollY = window.scrollY;
const header = document.querySelector('header');

window.addEventListener('scroll', () => {
  const currentScrollY = window.scrollY;
  
  // Se scorri verso il basso e superi i 50px, nascondi l'header
  if (currentScrollY > lastScrollY && currentScrollY > 50) {
    header.classList.add('header-hidden');
  } else {
    // Se scorri verso l'alto, mostralo di nuovo
    header.classList.remove('header-hidden');
  }
  
  lastScrollY = currentScrollY;
});