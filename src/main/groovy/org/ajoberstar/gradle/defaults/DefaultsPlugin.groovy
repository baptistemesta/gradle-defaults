/*
 * Copyright 2014-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ajoberstar.gradle.defaults

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar

class DefaultsPlugin implements Plugin<Project> {

	void apply(Project project) {
    if (project.rootProject != project) {
      throw new GradleException('org.ajoberstar.defaults must only be applied to the root project')
    }
		addGit(project)
    addSonar(project)
		addReleaseConfig(project)

		project.allprojects { prj ->
      addSpotless(project)
			addJavaConfig(prj)
			addGroovyConfig(prj)
			addMavenPublishingConfig(prj)
      addGradlePluginConfig(prj)
			addOrderingRules(prj)
		}
	}


	private void addGit(Project project) {
		project.plugins.apply('org.ajoberstar.git-publish')

		def addOutput = { task ->
			project.githubPages.pages.from(task.outputs.files) {
				into "docs${task.path}".replace(':', '/')
			}
		}

		project.allprojects { prj ->
			prj.plugins.withId('java') { addOutput(prj.javadoc) }
			prj.plugins.withId('groovy') { addOutput(prj.groovydoc) }
		}

		project.gitPublish {
			contents {
				from 'src/gh-pages'
			}
		}
	}

  private void addSonar(Project project) {
    project.plugins.apply('org.sonarqube')
    project.sonarqube {
      properties {
          property 'sonar.projectVersion', version.toString().split('-')[0]
      }
    }
  }

  private void addSpotless(Project project) {
    project.plugins.apply('com.diffplug.gradle.spotless')
    project.spotless {
      java {
        target 'src/**/*.java'
        googleJavaFormat()
        licenseHeaderFile project.rootProject.file('gradle/HEADER')
      }
      format 'groovy', {
        target 'src/**/*.groovy'
        trimTrailingWhitespace()
        indentWithSpaces(2)
        endWithNewline()
        licenseHeaderFile project.rootProject.file('gradle/HEADER')
      }
    }
  }

	private void addJavaConfig(Project project) {
		project.plugins.withId('java') {
			project.plugins.apply('jacoco')

			Task sourcesJar = project.tasks.create('sourcesJar', Jar)
			sourcesJar.with {
				classifier = 'sources'
				from project.sourceSets.main.allSource
			}

			Task javadocJar = project.tasks.create('javadocJar', Jar)
			javadocJar.with {
				classifier = 'javadoc'
				from project.tasks.javadoc.outputs.files
			}
		}
	}

	private void addGroovyConfig(Project project) {
		project.plugins.withId('groovy') {
			Task groovydocJar = project.tasks.create('groovydocJar', Jar)
			groovydocJar.with {
				classifier = 'groovydoc'
				from project.tasks.groovydoc.outputs.files
			}
		}
	}

	private void addReleaseConfig(Project project) {
		project.plugins.apply('org.ajoberstar.release-experimental')
    def releaseTask = project.tasks.release
    releaseTask.dependsOn 'gitPublishPush'
    project.allprojects { prj ->
        prj.plugins.withId('org.gradle.base') {
            releaseTask.dependsOn prj.clean, prj.build
        }
        prj.plugins.withId('maven-publish') {
            releaseTask.dependsOn prj.publish
        }
    }
	}

	private void addOrderingRules(Project project) {
		project.plugins.withId('org.gradle.base') {
			def clean = project.tasks['clean']
			project.tasks.all { task ->
				if (task != clean) {
					task.shouldRunAfter clean
				}
			}

			def build = project.tasks['build']
			project.tasks.all { task ->
				if (task.group == 'publishing') {
					task.shouldRunAfter build
				}
			}
		}
	}

	private void addMavenPublishingConfig(Project project) {
		project.plugins.withId('maven-publish') {
			project.publishing {
				publications {
					main(MavenPublication) {
						project.plugins.withId('java') {
							from project.components.java
							artifact project.sourcesJar
							artifact project.javadocJar
						}

						project.plugins.withId('groovy') {
							artifact project.groovydocJar
						}
					}
				}
				repositories {
					mavenLocal()
          maven {
            url = "https://api.bintray.com/maven/${System.env['BINTRAY_USER']}/maven/${project.rootProject.name}/;publish=1"
            credentials {
              username = System.env['BINTRAY_USER']
              password = System.env['BINTRAY_KEY']
            }
          }
				}
			}
		}
	}

	private void addGradlePluginConfig(Project project) {
		project.plugins.withId('java-gradle-plugin') {
      project.sourceSets {
          compatTest
      }

      project.gradlePlugin {
          testSourceSets sourceSets.compatTest
      }

      project.task compatTest
      project.check.dependsOn compatTest

      project.supportedGradleVersions.each { gradleVersion ->
          def task = project.tasks.create("compatTest${gradleVersion}", Test) {
              testClassesDir = project.sourceSets.compatTest.output.classesDir
              classpath = project.sourceSets.compatTest.runtimeClasspath
              systemProperty 'compat.gradle.version', gradleVersion
          }
          project.compatTest.dependsOn task
      }
		}

    project.plugins.withId('com.gradle.plugin-publish') {
      // a special plugin publication will exist. don't try to duplicate it
      project.publishMainPublicationToBintrayRepository.enabled = false
    }
	}
}
