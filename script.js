document.addEventListener('DOMContentLoaded', () => {
    initNavbar();
    initLightbox();
    initTestimonialSlider();
    initScrollReveal();
    initActiveNavOnScroll();
    initContactForm();
    document.getElementById('currentYear').textContent = new Date().getFullYear();
});

/* ---------- NAVBAR ---------- */
function initNavbar() {
    const toggle = document.getElementById('navbarToggle');
    const nav = document.getElementById('navbarNav');
    if (!toggle || !nav) return;

    function openMenu() {
        nav.classList.add('open');
        nav.style.visibility = 'visible';
        toggle.setAttribute('aria-expanded', 'true');
        document.body.style.overflow = 'hidden';
    }

    function closeMenu() {
        nav.classList.remove('open');
        nav.style.visibility = 'hidden';
        toggle.setAttribute('aria-expanded', 'false');
        document.body.style.overflow = '';
    }

    toggle.addEventListener('click', (e) => {
        e.stopPropagation();
        if (nav.classList.contains('open')) closeMenu();
        else openMenu();
    });

    nav.querySelectorAll('.navbar__link').forEach(link => {
        link.addEventListener('click', closeMenu);
    });

    document.addEventListener('click', (e) => {
        if (nav.classList.contains('open') && !nav.contains(e.target) && e.target !== toggle) {
            closeMenu();
        }
    });
}

/* ---------- LIGHTBOX ---------- */
function initLightbox() {
    const lightbox = document.getElementById('lightbox');
    const img = document.getElementById('lightboxImage');
    const closeBtn = document.getElementById('lightboxClose');
    const prev = document.getElementById('lightboxPrev');
    const next = document.getElementById('lightboxNext');
    const items = document.querySelectorAll('.gallery__item');
    if (!lightbox || !img) return;

    const images = Array.from(items).map(item => item.querySelector('img').src);
    let index = 0;

    function open(i) { index = i; img.src = images[index]; lightbox.classList.add('active'); document.body.style.overflow = 'hidden'; }
    function close() { lightbox.classList.remove('active'); document.body.style.overflow = ''; }
    function nextImg() { index = (index + 1) % images.length; img.src = images[index]; }
    function prevImg() { index = (index - 1 + images.length) % images.length; img.src = images[index]; }

    items.forEach((item, i) => item.addEventListener('click', () => open(i)));
    closeBtn.addEventListener('click', close);
    lightbox.addEventListener('click', (e) => { if (e.target === lightbox) close(); });
    prev.addEventListener('click', (e) => { e.stopPropagation(); prevImg(); });
    next.addEventListener('click', (e) => { e.stopPropagation(); nextImg(); });
    document.addEventListener('keydown', (e) => {
        if (!lightbox.classList.contains('active')) return;
        if (e.key === 'Escape') close();
        if (e.key === 'ArrowRight') nextImg();
        if (e.key === 'ArrowLeft') prevImg();
    });
}

/* ---------- TESTIMONIAL SLIDER ---------- */
function initTestimonialSlider() {
    const track = document.getElementById('testimonialTrack');
    const dots = document.querySelectorAll('.testimonials__dot');
    if (!track || dots.length === 0) return;
    let current = 0;
    const total = dots.length;
    let interval;

    function goTo(i) {
        current = i;
        track.style.transform = `translateX(-${current * 100}%)`;
        dots.forEach((d, idx) => d.classList.toggle('active', idx === current));
    }
    function nextSlide() { current = (current + 1) % total; goTo(current); }
    function startAuto() { interval = setInterval(nextSlide, 5000); }
    function reset() { clearInterval(interval); startAuto(); }

    dots.forEach(dot => dot.addEventListener('click', () => { goTo(parseInt(dot.dataset.index)); reset(); }));

    let startX = 0;
    track.addEventListener('touchstart', (e) => { startX = e.touches[0].clientX; });
    track.addEventListener('touchend', (e) => {
        const diff = startX - e.changedTouches[0].clientX;
        if (Math.abs(diff) > 40) {
            if (diff > 0) current = (current + 1) % total;
            else current = (current - 1 + total) % total;
            goTo(current);
            reset();
        }
    });
    startAuto();
}

/* ---------- SCROLL REVEAL ---------- */
function initScrollReveal() {
    const elements = document.querySelectorAll('.reveal');
    if (elements.length === 0) return;
    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const delay = entry.target.dataset.delay || 0;
                setTimeout(() => entry.target.classList.add('visible'), parseInt(delay));
                observer.unobserve(entry.target);
            }
        });
    }, { threshold: 0.15 });
    elements.forEach(el => observer.observe(el));
}

/* ---------- ACTIVE NAV ON SCROLL ---------- */
function initActiveNavOnScroll() {
    const sections = document.querySelectorAll('section[id]');
    const links = document.querySelectorAll('.navbar__link[data-nav]');
    window.addEventListener('scroll', () => {
        const scrollY = window.scrollY + 100;
        let current = '';
        sections.forEach(sec => {
            if (scrollY >= sec.offsetTop - 150 && scrollY < sec.offsetTop + sec.offsetHeight) {
                current = sec.getAttribute('id');
            }
        });
        links.forEach(link => link.classList.toggle('active', link.dataset.nav === current));
    });
}

/* ---------- CONTACT FORM ---------- */
function initContactForm() {
    const form = document.getElementById('contactForm');
    if (!form) return;
    const name = document.getElementById('fullName');
    const phone = document.getElementById('phone');
    const course = document.getElementById('courseSelect');

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        let valid = true;
        document.getElementById('fullNameError').textContent = '';
        document.getElementById('phoneError').textContent = '';
        document.getElementById('courseError').textContent = '';

        if (name.value.trim().length < 3) {
            document.getElementById('fullNameError').textContent = 'Enter valid name';
            valid = false;
        }
        if (!/^[6-9]\d{9}$/.test(phone.value.trim())) {
            document.getElementById('phoneError').textContent = 'Valid 10-digit number required';
            valid = false;
        }
        if (!course.value) {
            document.getElementById('courseError').textContent = 'Select a course';
            valid = false;
        }

        if (valid) {
            alert('We will contact you on WhatsApp.');
            form.reset();
        }
    });
}