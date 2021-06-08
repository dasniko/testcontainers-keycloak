/**
 * This package only exists because of Testcontainer's hard dependency to JUnit4.
 * <p>
 * This project doesn't use JUnit4 any more, only JUnit 5 (Jupiter) tests are supported.
 * To meet the needs of the {@link org.testcontainers.containers.GenericContainer} class on build and run time,
 * this package was added to serve the needed classes/interfaces as empty dummy artifacts.
 * </p>
 * <p>
 * As soon as Testcontainers doesn't need the JUnit4 dependency any more, this package will be removed.
 * </p>
 *
 * @see <a href="https://github.com/testcontainers/testcontainers-java/issues/970">https://github.com/testcontainers/testcontainers-java/issues/970</a>
 */
package org.junit;
