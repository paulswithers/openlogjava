XPages OpenLog Logger
==================



Java classes and OSGi Plugin for OpenLog from Java and SSJS.
A PhaseListener automatically generates OpenLog errors / events for any SSJS errors / events.


The OSGi plugin has been incorporated into this repository since M5.



The JavaDoc is now in the Documentation folder


Note: The repository includes a non-Extension Library version. This is deprecated now. It works but is not being actively developed and fixes are not being applied. One version is because of the overhead of supporting the Extension Library version, the OSGi plugin version and the OpenNTF Domino API version. The more over-arching reason is the age of Domino 8.5.3 and my belief that developing (particularly web) applications using base XPages is unlikely to adequately compete with modern XPages or web applications. Components like the Dialog control, UI options like Bootstrap, plus performance enhancements since 8.5.3 mean a non-XPages application look and feel dated.

There are various configuration options. See the documentation.