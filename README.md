# Lunar Pack Organizer Patch

This patches the bug that is causing modern version (1.20) of Lunar Client's pack organizer to take a couple seconds ~ minutes to load the resource pack screen

This Fabric mod (that you can use on Lunar Client, with no effects on vanilla clients) patches Lunar Client and fix the issue.

You likely don't need it for Linux (because it likely only happens on Windows, because the file system API is slower on it)

## Warning
Only works on x86_64 Windows & x86_64 Linux, please check the status of [fan87/Java-Injector on GitHub](https://github.com/fan87/Java-Injector), which only supports x86_64 windows & linux as for now.

However, I'm planning for a complete rewrite of Java-Injector (at the time of writing), if you are interested working on it together, reach out to me via email `fan87.tw@gmail.com` or Discord `fan87`!


### Technical Detail
When you have a lot of unzipped resource packs (resource packs that's not a zip file, but a directory), Lunar Client will try to look into it recursively
and (I suppose) does that for every single resource pack you have (have not confirmed or checked that).

A possible more efficient algorithm for that could be by caching where the pack's location is, or does the search in a single run - 
because it rarely looks for just a single, looking through the entire directory and caching every resource packs (dir with .mcmeta or .zip) makes more sense. 


TL;DR, the bug is caused by poorly written algorithm + Windows' slow IO speed
