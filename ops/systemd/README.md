Copy the service and timer to `/etc/systemd/system/` on the VPS only after
adjusting `WorkingDirectory` and the deployment user. Then run:

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now campuscore-backup.timer
systemctl list-timers campuscore-backup.timer
```

Off-site replication is intentionally not enabled until an approved object
storage target and retention policy exist.
