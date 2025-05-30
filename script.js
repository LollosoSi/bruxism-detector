


const contentDiv = document.getElementById('content');
const mainInner = document.querySelector('.main-inner');

const navLinks = Array.from(document.querySelectorAll('nav a'));
const sectionsOrder = navLinks.map(link => link.getAttribute('data-section'));

let currentContainer = null;
let currentIndex = 0;  // Index of currently loaded section

async function loadSection(name, direction) {
  const newContainer = document.createElement('div');
  newContainer.classList.add('content-container', 'active');
  newContainer.style.opacity = '0';
  newContainer.style.pointerEvents = 'none';

  // Load content
  const res = await fetch(`sections/${name}.html`);
  if (res.ok) {
    newContainer.innerHTML = await res.text();
  } else {
    newContainer.innerHTML = `<section><h2>Error</h2><p>Could not load section "${name}".</p></section>`;
  }

  mainInner.appendChild(newContainer);

  if (!currentContainer) {
    // First load, no animation
    newContainer.style.opacity = '1';
    newContainer.style.pointerEvents = 'auto';
    currentContainer = newContainer;
    currentIndex = sectionsOrder.indexOf(name);
    return;
  }

  // Determine animation classes based on direction
  // 'left' means new content comes from left (old slides out right)
  // 'right' means new content comes from right (old slides out left)
  const outClass = direction === 'left' ? 'slide-out-right' : 'slide-out-left';
  const inClass = direction === 'left' ? 'slide-in-left' : 'slide-in-right';

  currentContainer.classList.add(outClass);
  newContainer.classList.add(inClass);

  newContainer.style.opacity = '1';
  newContainer.style.pointerEvents = 'auto';

  function onAnimationEnd() {
    currentContainer.classList.remove('active', outClass);
    mainInner.removeChild(currentContainer);

    newContainer.classList.remove(inClass);
    newContainer.style.opacity = '1';
    newContainer.style.pointerEvents = 'auto';

    currentContainer = newContainer;
    currentIndex = sectionsOrder.indexOf(name);
    newContainer.removeEventListener('animationend', onAnimationEnd);
  }

  newContainer.addEventListener('animationend', onAnimationEnd);
}

// Add event listeners with direction logic
navLinks.forEach(link => {
  link.addEventListener('click', e => {
    e.preventDefault();
    const section = link.getAttribute('data-section');
    if (!section || section === sectionsOrder[currentIndex]) return; // no change

    const clickedIndex = sectionsOrder.indexOf(section);
    const direction = clickedIndex > currentIndex ? 'right' : 'left';

    loadSection(section, direction);
  });
});

// Load initial section
loadSection(sectionsOrder[0], 'right');


const cct = document.querySelector('.content-container');
const header = document.querySelector('header');
const nav = document.querySelector('nav');
const footer = document.querySelector('footer');

let lastScrollY = 0;
let ticking = false;

function onMainScroll() {
  const currentScrollY = cct.scrollTop;

  if (!ticking) {
    window.requestAnimationFrame(() => {
      if (currentScrollY > lastScrollY && currentScrollY > 20) {
        header.classList.add('hidden');
        nav.classList.add('hidden');
        footer.classList.add('hidden');
        console.log('Hiding footer');
      } else if (currentScrollY < lastScrollY - 10 || currentScrollY <= 20) {
        header.classList.remove('hidden');
        nav.classList.remove('hidden');
		document.querySelector('footer').classList.add('hidden');

        console.log('Showing footer');
      }

      lastScrollY = currentScrollY;
      ticking = false;
    });

    ticking = true;
  }
}

document.addEventListener('DOMContentLoaded', () => {
  const el = document.getElementById('contentA');
  el.addEventListener('scroll', () => {
    console.log('scroll event fired', el.scrollTop);
  });
});
