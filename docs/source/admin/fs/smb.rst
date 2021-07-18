.. _smb-settings:

SMB settings
------------

You can index files remotely using SMB.

Here is a list of SMB settings (under ``server.`` prefix):

+-----------------------+-----------------------+-----------------------+
| Name                  | Default value         | Documentation         |
+=======================+=======================+=======================+
| ``server.hostname``   | ``null``              | Hostname              |
+-----------------------+-----------------------+-----------------------+
| ``server.username``   | ``anonymous``         | :ref:`smb_login`      |
+-----------------------+-----------------------+-----------------------+
| ``server.password``   | ``null``              | :ref:`smb_login`      |
+-----------------------+-----------------------+-----------------------+
| ``server.protocol``   | ``"local"``           | Set it to ``smb``     |
+-----------------------+-----------------------+-----------------------+

.. _smb_login:

Username / Password
~~~~~~~~~~~~~~~~~~~

Let’s say you want to index from a remote server using SMB:

-  FS URL: ``/path/to/data/dir/on/server``
-  Server: ``mynode.mydomain.com``
-  Username: ``username`` (default to ``anonymous``)
-  Password: ``password``
-  Protocol: ``smb`` (default to ``local``)

.. code:: yaml

   name: "test"
   fs:
     url: "/path/to/data/dir/on/server"
   server:
     hostname: "mynode.mydomain.com"
     username: "username"
     password: "password"
     protocol: "smb"
