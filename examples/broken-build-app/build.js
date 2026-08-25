'use strict';

/**
 * Build step that fails for the most ordinary reason there is: it imports a package that is used in
 * source but was never added to `dependencies`.
 *
 * Locally this often works because the package is still in a stale `node_modules`. In a clean container
 * build it fails immediately - which is exactly the class of failure a deployment platform exists to
 * catch, and a good test of whether failure analysis is useful.
 */

console.log('> preparing assets');
console.log('> resolving modules');

// Not declared in package.json on purpose.
const slugify = require('slugify');

console.log(slugify('this line is never reached'));
