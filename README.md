![Reload](https://github.com/jprante/elasticsearch-plugin-deploy/raw/master/src/site/resources/Reload_icon.svg.png)

Image by [Wikimedia](http://commons.wikimedia.org/wiki/File:Reload_icon.svg)

# Deploy plugin for Elasticsearch

With this plugin, refreshable Elasticsearch plugins can be installed.
This is convenient for managing plugins without restarting nodes.

Example:

    curl -XPOST 'localhost:9200/_deploy' -d '{
        "name" : "test",
        "path" : "/Users/joerg/Projects/github/jprante/elasticsearch-test/target/elasticsearch-plugin-test-1.4.0.0.jar"
    }'

    curl -XPOST 'localhost:9200/_deploy' -d '{
        "name" : "demo",
        "path" : "http://example.org/elasticsearch-plugin-demo-1.4.0.0.zip"
    }'

in case of success, the answer of the server looks like this

    {"nodes":[{"name":"Melee","success":true}],"deployed":true}

If a deploy is repeated, the services of an existing plugin with the same name are stopped
and the new plugin replaces the old one.

Deployed plugins are stored under the deploy plugins' directory in the Elasticsearch plugins folder.

If URLs are to be used in `path` for remote access, the URL domain has to be configured beforehand
in Elasticsearch node settings in a list of permitted domains.

    plugins.deploy.domains:
        - foo.com
        - bar.org
        - baz

It is possible to get a list of all deployable plugins with

    curl -XGET 'localhost:9200/_deploy'
    {"nodes":[{"name":"Melee","plugins":{"library":"org.xbib.elasticsearch.plugin.library.LibraryPlugin@3564cbc2"}}],"deployed":true}

To remove a deploayble plugin, the files in the deploy plugin folder must be removed and the node must be restarted.

# WARNING

The plugin should only be used by Elasticsearch administrators. Unauthorized plugin installs can fetch unknown
code from URLs, execute arbitrary code, open ports and may expose systems to unknown risks.

Use at your own risk!

## Versions

| Release date | Plugin version | Elasticsearch version |
| -------------| ---------------| ----------------------|
| Dec 26, 2014 | 1.4.0.0        | 1.4.0                 |

## Installation

    ./bin/plugin --install deploy --url http://xbib.org/repository/org/xbib/elasticsearch/plugin/elasticsearch-plugin-deploy/1.4.0.0/elasticsearch-plugin-deploy-1.4.0.0-plugin.zip

## Project docs

The Maven project site is available at [Github](http://jprante.github.io/elasticsearch-plugin-deploy)

## Issues

All feedback is welcome! If you find issues, please post them at
[Github](https://github.com/jprante/elasticsearch-plugin-deploy/issues)

# License

Elasticsearch Deploy Plugin

Copyright (C) 2014 Jörg Prante

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
