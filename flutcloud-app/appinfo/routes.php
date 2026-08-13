<?php

declare(strict_types=1);

/**
 * OCS API routes of the FlutCloud app.
 *
 * All routes are prefixed by Nextcloud with /ocs/v2.php/apps/flutcloud.
 * The FlutLink client queries the OCS capabilities endpoint
 * (/ocs/v2.php/cloud/capabilities?format=json) and refuses to connect unless
 * the server advertises the `flutcloud` capability.
 */
return [
    'ocs' => [
        ['name' => 'Api#ping', 'url' => '/api/v1/ping', 'verb' => 'GET'],
        ['name' => 'Api#links', 'url' => '/api/v1/links', 'verb' => 'GET'],
        ['name' => 'Api#createLink', 'url' => '/api/v1/links', 'verb' => 'POST'],
        ['name' => 'Api#deleteLink', 'url' => '/api/v1/links/{name}', 'verb' => 'DELETE'],
        ['name' => 'Api#parts', 'url' => '/api/v1/parts', 'verb' => 'GET'],
        ['name' => 'Api#createPart', 'url' => '/api/v1/parts', 'verb' => 'POST'],
        ['name' => 'Api#ensureProjectFolder', 'url' => '/api/v1/project-folder', 'verb' => 'POST'],
    ],
];
