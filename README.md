# Tidal-syntax-for-SClang
Use Tidal syntax to play with NodeProxy objects.

## The idea

When exploring Tidal i found the short syntax quite nice to use.

This project tries to create that in SClang.

The basic idea is this:

```
NP(~a).snd("bd:2 hh:1 cl:1 sn:7").play(0.7).mon(0.5);
```

The NP class is given a NodeProxy, and has (possibly many) chained methods.
These methods will operate on the NodeProxy.

NP will initialize the NodeProxy with:
```
proxy.ar(2);
proxy.source = { Silent.ar()!2 };
```

The ```snd()``` method is given a specification what sound(s) to play.  
This specification is parsed and then stored in the NP class.
The specification above specifies 4 steps in a sequence.  
The first step should play a "bd" sound.  
If there is a map ```samples/bd``` available, then the third sample file found there will be played (using a built-in Synthdef which can play a sample file).  
If ```samples/bd``` is not available, then a Synthdef added as ```\bd``` will be played.

The structure / timing for the Pbind can also be derived from the given specification. The specification above shows us 4 steps (notes) to play, so the resulting durations should be ```[1/4, 1/4, 1/4, 1/4]``` used in a Pseq in the Pbind.

More information about this "mini-notation" can be found in various Tidal tutorials or documentation. The NPParser class (in np_mininotation.sc) takes care of parsing the specifications in mini-notation format.

The ```play()``` method will create a Pbind on the NodeProxy, using the information that has been stored inside the NP object by other methods that were called before ```play()``` was called.

The parameter to ```play()``` is the volume with which the Pbind should play on the NodeProxy private bus.

The Pbind is added by NP on slot 10 at the moment, but of course this number should become a parameter, so that you can add multiple Pbinds to a proxy using multiple NP objects that operate on one proxy. One caveat: when a NP object initializes the _source_ of the NodeProxy, all the slots will be cleared! Needs some thinking first.

The ```mon()``` method will call ```play``` on the proxy, and the parameter to ```mon()``` is the monitor volume to use. You could also play the proxy directly, instead of through the NP class. (```~a.vol_(0.5).play```)

The mininotation parser supports nested steps using ```[ ]``` and alternating steps using ```< >``` and supplying numbers with a colon ```bd:3```.
More to be added later.

Other methods controlling the ```number()``` or ```amp()``` could also have a mininotation specification as parameter.  
With ```amp()``` you could then play accents or ghost notes.

In Tidal you can say which of the given specifications finally determines the 'structure', that means the durations for the Pbind. In NP i will make two versions for each method: one regular, and one postfixed with an ```_``` character. If you call ```snd_()``` then the given specification for the sound will be used to create the structure for Pbind. The other specified data will "wrap along" in the Pbind.

The rule will be that the _first_ called method that takes a specification will determine the structure for the Pbind. But the _last_ called ```xxx_``` method that takes a specification will override that.

Lastly, the ```beats()``` method lets you specify howmany beats the given structure should represent. The default is 1 beat, but you could say 1.345 beats for example. The durations will then be all multiplied by this number.

So far the description and ideas for this project.

## Using it in SuperCollider

I use ```git clone``` to get the sources somewhere on my disk.

Then i go inside the ```~/.local/share/SuperCollider/Extensions``` folder, and i place there a _symbolic link_ to the folder where the sources are. Like this: ```ln -s ~/repos/Tidal-syntax-for-SClang np```.

Then start SuperCollider, recompile the classes and the NP class should be avilable. It appears to be that SuperCollider will follow symbolic links.

One thing i need to do is give the NP class proper documentation so that SuperCollider will display the documentation in its class browser. Will figure that out.

## Small example

Create a ```.scd``` file with this code in it:

```
s.boot;

NPSamples.load;

p = ProxySpace.push(s).makeTempoClock(100/60).quant_(2);

NP(~a).snd("bd:2 [sn:1 sn:1] ~ bd:2").play(0.5).mon(0.3);

NP(~b).snd("~ ~ [hh hh hh] ~").play(0.5).mon(0.3);

p.clock.tempo_(60/60)
```

Load it in SuperCollider.

Also make sure that in the _same_ folder as where you created this file, a subfolder exists named ```samples``` and that this folder again contains subfolders with sample files in it (.wav, .aiff, etc).

Then evaluate the lines above one by one. Have fun.

## Example setting the structure and using amps and beats

```
s.boot;

NPSamples.load;

p = ProxySpace.push(s).makeTempoClock(60/60).quant_(2);

NP(~a).snd("bd <~ [~ bd:2]> ~ ~ sn ~ ~ ~").beats(2).play(0.5).mon(0.3);

NP(~b).snd("~ ~ hh hh").amp_("0.2 0.8 0.2").play(0.5).mon(0.4);

NP(~c).snd("rd").num_("1 1 1 3").play(0.5).mon(0.1);
```

## More ideas to realize

```bin("10001010")```

```bin``` could be used to declare the structure, thereby overriding all other structure defining method calls.

```hex("92")```

Just like ```bin``` you can think of much shorter hexadecimal notation. "92" in hex equals binary "10010010".

## Tidal syntax not supported yet

```,``` paralell running steps

```!``` repeats the last step, e.g.: "bd bd bd" = "bd bd !" = "bd!3"

```*``` "bd sn*2" = "bd [sn sn]" plays 2 snares in same step, so speeds up

```/``` "bd sn/2" = "bd <sn ~>" plays 1/2 snare in one step, next cycle
        it plays the other half of the snare, which yields silence.
        slows down

```@``` [bd sn@0.5] makes duration of the snare half as long

```bd(3,8)``` euclidian rhythms ( bd:2(3,8) is also possible )

## Latest developments

20211128: parsing */@ works, now use it during play..

