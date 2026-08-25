<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Listener;

use OCA\FlutCloud\Dav\GuestLockPlugin;
use OCA\DAV\Events\SabrePluginAddEvent;
use OCP\AppFramework\IAppContainer;
use OCP\EventDispatcher\Event;
use OCP\EventDispatcher\IEventListener;

/**
 * Registers [GuestLockPlugin] on every Sabre DAV server instance so guest
 * subfolder locks also apply to Nextcloud's native public WebDAV endpoint.
 */
final class RegisterDavLockPlugin implements IEventListener
{
    private IAppContainer $container;

    public function __construct(IAppContainer $container)
    {
        $this->container = $container;
    }

    public function handle(Event $event): void
    {
        if (!$event instanceof SabrePluginAddEvent) {
            return;
        }
        $event->getServer()->addPlugin($this->container->get(GuestLockPlugin::class));
    }
}
