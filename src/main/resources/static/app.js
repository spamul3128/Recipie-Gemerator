/**
 * Smart Recipe Generator – app.js
 *
 * Responsibilities:
 *  - Ingredient tag management (add / remove)
 *  - Dietary restriction selection
 *  - POST to /api/generate-recipes
 *  - Render recipe cards
 *  - Real-time metric ↔ imperial unit conversion
 *  - Client-side input sanitization
 */

'use strict';

// ── State ──────────────────────────────────────────────────────────────────
let ingredients   = [];
let isImperial    = false;
let currentRecipes = [];

// ── DOM refs ───────────────────────────────────────────────────────────────
const ingredientInput   = document.getElementById('ingredient-input');
const tagContainer      = document.getElementById('tag-container');
const ingredientCount   = document.getElementById('ingredient-count');
const generateBtn       = document.getElementById('generate-btn');
const loadingWrap       = document.getElementById('loading');
const errorBox          = document.getElementById('error-box');
const errorMessage      = document.getElementById('error-message');
const resultsSection    = document.getElementById('results-section');
const recipesGrid       = document.getElementById('recipes-grid');
const unitToggle        = document.getElementById('unit-toggle');
const generationTimeEl  = document.getElementById('generation-time');

// ── Client-side sanitization ───────────────────────────────────────────────
/**
 * Sanitize a raw ingredient string:
 *  - Strip HTML tags and entities
 *  - Allow only safe characters (letters, digits, spaces, hyphens, etc.)
 *  - Trim and truncate to 50 chars
 */
function sanitizeText(raw) {
  return raw
    .replace(/<[^>]*>/g, '')           // HTML tags
    .replace(/&[a-zA-Z]+;/g, '')       // HTML entities
    .replace(/[^\w\s\-',.()/]/g, '')   // Dangerous chars
    .trim()
    .slice(0, 50);
}

// ── Ingredient tag management ──────────────────────────────────────────────
function addIngredient(raw) {
  const name = sanitizeText(raw).toLowerCase();
  if (!name || ingredients.includes(name)) return;
  if (ingredients.length >= 30) {
    showInlineError('Maximum 30 ingredients allowed.');
    return;
  }
  ingredients.push(name);
  renderTags();
}

function removeIngredient(name) {
  ingredients = ingredients.filter(i => i !== name);
  renderTags();
}

function renderTags() {
  // Remove existing tag elements (not the input)
  tagContainer.querySelectorAll('.ingredient-tag').forEach(el => el.remove());

  ingredients.forEach(name => {
    const tag = document.createElement('span');
    tag.className = 'ingredient-tag';

    const label = document.createTextNode(name);
    const btn   = document.createElement('button');
    btn.type        = 'button';
    btn.textContent = '×';
    btn.title       = `Remove ${name}`;
    btn.addEventListener('click', () => removeIngredient(name));

    tag.appendChild(label);
    tag.appendChild(btn);
    tagContainer.insertBefore(tag, ingredientInput);
  });

  // Update counter & button state
  ingredientCount.textContent =
    ingredients.length === 0 ? '0 ingredients added'
    : ingredients.length === 1 ? '1 ingredient added'
    : `${ingredients.length} ingredients added`;

  generateBtn.disabled = ingredients.length === 0;
}

// ── Ingredient input events ────────────────────────────────────────────────
ingredientInput.addEventListener('keydown', e => {
  if (e.key === 'Enter' || e.key === ',') {
    e.preventDefault();
    if (ingredientInput.value.trim()) {
      addIngredient(ingredientInput.value);
      ingredientInput.value = '';
    }
  } else if (e.key === 'Backspace' && ingredientInput.value === '' && ingredients.length > 0) {
    removeIngredient(ingredients[ingredients.length - 1]);
  }
});

ingredientInput.addEventListener('blur', () => {
  if (ingredientInput.value.trim()) {
    addIngredient(ingredientInput.value);
    ingredientInput.value = '';
  }
});

// Allow clicking anywhere in the container to focus the input
tagContainer.addEventListener('click', e => {
  if (e.target === tagContainer) ingredientInput.focus();
});

// ── Generate recipes ───────────────────────────────────────────────────────
generateBtn.addEventListener('click', async () => {
  if (ingredients.length === 0) return;

  const dietaryRestrictions = Array.from(
    document.querySelectorAll('.dietary-checkbox:checked')
  ).map(cb => cb.value);

  showLoading();

  // Client-side timeout guard (30 s)
  const controller = new AbortController();
  const timeoutId  = setTimeout(() => controller.abort(), 30_000);

  try {
    const response = await fetch('/api/generate-recipes', {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      signal:  controller.signal,
      body:    JSON.stringify({ ingredients, dietaryRestrictions })
    });

    clearTimeout(timeoutId);
    const data = await response.json();

    if (!response.ok) {
      throw new Error(data.error || `Server error ${response.status}`);
    }

    currentRecipes = data.recipes || [];
    if (currentRecipes.length === 0) throw new Error('No recipes were returned. Please try again.');

    renderRecipes(currentRecipes);
    showResults(data.generationTimeMs);

  } catch (err) {
    clearTimeout(timeoutId);
    if (err.name === 'AbortError') {
      showError('Request timed out. The AI is taking too long — please try again.');
    } else {
      showError(err.message);
    }
  }
});

// ── Unit conversion ────────────────────────────────────────────────────────
unitToggle.addEventListener('change', () => {
  isImperial = unitToggle.checked;
  if (currentRecipes.length > 0) renderRecipes(currentRecipes);
});

/**
 * Ordered list of metric unit patterns and their imperial converters.
 * Each entry: { pattern: RegExp, convert: (value: number, unit: string) => string }
 */
const UNIT_RULES = [
  // Temperature  – must come before bare 'c' check
  { pattern: /^(\d+(?:\.\d+)?)\s*°[Cc]$/,         convert: v => `${Math.round(v * 9/5 + 32)}°F` },
  // Weight
  { pattern: /^(\d+(?:\.\d+)?)\s*kg$/i,            convert: v => `${(v * 2.205).toFixed(2)} lbs` },
  { pattern: /^(\d+(?:\.\d+)?)\s*g$/i,             convert: v => `${(v / 28.35).toFixed(1)} oz` },
  // Volume – litre variants
  { pattern: /^(\d+(?:\.\d+)?)\s*(?:litres?|liters?|L)$/i,
    convert: v => `${(v * 4.227).toFixed(1)} cups` },
  { pattern: /^(\d+(?:\.\d+)?)\s*ml$/i,
    convert: v => {
      if (v >= 240)  return `${(v / 240).toFixed(1)} cups`;
      if (v >= 15)   return `${Math.round(v / 15)} tbsp`;
      return               `${Math.round(v / 5)} tsp`;
    }
  },
  // Length
  { pattern: /^(\d+(?:\.\d+)?)\s*cm$/i, convert: v => `${(v / 2.54).toFixed(1)} in` },
  { pattern: /^(\d+(?:\.\d+)?)\s*mm$/i, convert: v => `${(v / 25.4).toFixed(2)} in` },
];

function convertAmount(amount) {
  if (!isImperial) return amount;
  for (const { pattern, convert } of UNIT_RULES) {
    const m = amount.match(pattern);
    if (m) return convert(parseFloat(m[1]));
  }
  return amount; // unchanged (tbsp, tsp, cups, pieces, etc.)
}

// ── Render helpers ─────────────────────────────────────────────────────────
function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function renderRecipes(recipes) {
  recipesGrid.innerHTML = '';

  recipes.forEach((recipe, idx) => {
    const card = document.createElement('article');
    card.className = 'recipe-card';
    card.style.animationDelay = `${idx * 0.12}s`;

    // Dietary tags
    const dietaryHTML = (recipe.dietaryInfo || []).length > 0
      ? `<div class="dietary-tags">${
          recipe.dietaryInfo.map(t => `<span class="dietary-tag">${escapeHtml(t)}</span>`).join('')
        }</div>`
      : '';

    // Ingredients list
    const ingredientsHTML = (recipe.ingredients || []).map(ing =>
      `<li>
        <span class="amount">${escapeHtml(convertAmount(ing.amount))}</span>
        ${escapeHtml(ing.name)}
      </li>`
    ).join('');

    // Steps list
    const stepsHTML = (recipe.steps || []).map(step =>
      `<li><span class="step-instruction">${escapeHtml(step.instruction)}</span></li>`
    ).join('');

    card.innerHTML = `
      <div class="card-header">
        <span class="recipe-number">Recipe ${idx + 1} of ${recipes.length}</span>
        <h3>${escapeHtml(recipe.title)}</h3>
        ${dietaryHTML}
        <div class="recipe-meta">
          <span>⏱️ Prep: ${escapeHtml(recipe.prepTime)}</span>
          <span>🔥 Cook: ${escapeHtml(recipe.cookTime)}</span>
          <span>👤 Serves ${escapeHtml(String(recipe.servings))}</span>
        </div>
      </div>
      <p class="recipe-description">${escapeHtml(recipe.description)}</p>
      <div class="recipe-body">
        <div class="ingredients-section">
          <h4>🛒 Ingredients</h4>
          <ul class="ingredients-list">${ingredientsHTML}</ul>
        </div>
        <div class="steps-section">
          <h4>👨‍🍳 Instructions</h4>
          <ol class="steps-list">${stepsHTML}</ol>
        </div>
      </div>
    `;

    recipesGrid.appendChild(card);
  });
}

// ── UI state helpers ───────────────────────────────────────────────────────
function showLoading() {
  loadingWrap.hidden    = false;
  errorBox.hidden       = true;
  resultsSection.hidden = true;
  generateBtn.disabled  = true;
  generateBtn.querySelector('.btn-icon').textContent = '⏳';
}

function showResults(generationTimeMs) {
  loadingWrap.hidden    = false; // keep for a beat, then hide
  setTimeout(() => { loadingWrap.hidden = true; }, 200);
  errorBox.hidden       = true;
  resultsSection.hidden = false;
  generateBtn.disabled  = false;
  generateBtn.querySelector('.btn-icon').textContent = '✨';

  const secs = (generationTimeMs / 1000).toFixed(1);
  generationTimeEl.textContent = `⚡ Generated in ${secs}s`;

  resultsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function showError(message) {
  loadingWrap.hidden    = true;
  resultsSection.hidden = true;
  errorBox.hidden       = false;
  errorMessage.textContent = message;
  generateBtn.disabled  = false;
  generateBtn.querySelector('.btn-icon').textContent = '✨';
}

function showInlineError(message) {
  // Temporary inline feedback without hiding other sections
  const prev = document.getElementById('inline-error');
  if (prev) prev.remove();
  const el = document.createElement('p');
  el.id        = 'inline-error';
  el.className = 'helper-text';
  el.style.color = 'var(--red)';
  el.textContent = message;
  ingredientCount.insertAdjacentElement('afterend', el);
  setTimeout(() => el.remove(), 3000);
}

function dismissError() {
  errorBox.hidden = true;
}

