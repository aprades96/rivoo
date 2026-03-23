/**
 * Rivoo — Dynamic Tenant Branding for Keycloak Login
 *
 * Reads `salon_slug` from the URL query string and fetches the salon's
 * public profile from the Gateway. On success the page logo, heading,
 * and primary colour are replaced with the salon's branding.
 *
 * If the slug is missing or the fetch fails, the default Rivoo branding
 * that is already rendered in the HTML is kept untouched.
 */
(function () {
    'use strict';

    var GATEWAY_BASE = 'http://localhost:8080';
    var SALON_PUBLIC_API = GATEWAY_BASE + '/api/v1/salons/public/';

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Brighten or darken a hex colour by a fixed amount.
     *
     * @param {string} hex  - Colour in "#rrggbb" or "rrggbb" format.
     * @param {number} amount - Positive = lighter, negative = darker.
     * @returns {string} Adjusted hex colour with leading "#".
     */
    function adjustBrightness(hex, amount) {
        hex = hex.replace(/^#/, '');

        if (hex.length === 3) {
            hex = hex[0] + hex[0] + hex[1] + hex[1] + hex[2] + hex[2];
        }

        var r = Math.max(0, Math.min(255, parseInt(hex.substring(0, 2), 16) + amount));
        var g = Math.max(0, Math.min(255, parseInt(hex.substring(2, 4), 16) + amount));
        var b = Math.max(0, Math.min(255, parseInt(hex.substring(4, 6), 16) + amount));

        return '#' +
            ('0' + r.toString(16)).slice(-2) +
            ('0' + g.toString(16)).slice(-2) +
            ('0' + b.toString(16)).slice(-2);
    }

    /**
     * Extract a named query parameter from the current URL.
     *
     * @param {string} name - The parameter name.
     * @returns {string|null} The decoded value, or null if absent.
     */
    function getQueryParam(name) {
        var params = new URLSearchParams(window.location.search);
        return params.get(name);
    }

    // ---------------------------------------------------------------
    // Branding application
    // ---------------------------------------------------------------

    /**
     * Apply salon branding to the login page elements.
     *
     * @param {Object} salon - Salon public profile payload.
     * @param {string} salon.name - Display name of the salon.
     * @param {string} [salon.logoUrl] - URL of the salon logo.
     * @param {string} [salon.primaryColor] - Hex colour (e.g. "#6d28d9").
     */
    function applyBranding(salon) {
        // --- Salon name ---
        var heading = document.getElementById('rivoo-salon-name');
        if (heading && salon.name) {
            heading.textContent = salon.name;
        }

        // --- Logo ---
        var logo = document.getElementById('rivoo-logo');
        if (logo && salon.logoUrl) {
            logo.src = salon.logoUrl;
            logo.alt = (salon.name || 'Salon') + ' logo';
        }

        // --- Primary colour ---
        if (salon.primaryColor) {
            var color = salon.primaryColor;
            var root = document.documentElement;
            root.style.setProperty('--rivoo-primary', color);
            root.style.setProperty('--rivoo-primary-hover', adjustBrightness(color, 25));
        }
    }

    /**
     * Fetch salon data and apply branding.
     *
     * @param {string} slug - The salon's URL slug.
     */
    function fetchAndApplyBranding(slug) {
        var url = SALON_PUBLIC_API + encodeURIComponent(slug);

        fetch(url)
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('Salon API responded with status ' + response.status);
                }
                return response.json();
            })
            .then(function (salon) {
                applyBranding(salon);
            })
            .catch(function (err) {
                // Silently keep default Rivoo branding.
                console.warn('[rivoo-branding] Could not load salon branding:', err.message);
            });
    }

    // ---------------------------------------------------------------
    // Entry point
    // ---------------------------------------------------------------

    function init() {
        var slug = getQueryParam('salon_slug');
        if (slug) {
            fetchAndApplyBranding(slug);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
