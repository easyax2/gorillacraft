using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Experimental.Rendering;

namespace GorillaCraft
{
    /// <summary>
    /// Gorilla Tag's own gorilla, for Minecraft to draw: read from this player's running game (the
    /// mesh is readable in the build), so no Gorilla Tag files are ever copied or shipped.
    ///
    /// Model (sent once): the default body's skinned mesh (positions, normals, UVs, 4 bone weights
    /// per vertex), one submesh per material with its texture cut out of Gorilla Tag's texture
    /// atlas, the face mesh attached to its bone, and each bone's bind pose.
    ///
    /// Pose (every pose message): one 3x4 matrix per bone that takes a mesh-space point straight
    /// to Minecraft coordinates (relative to the head), i.e. Gorilla Tag's own animation and arm
    /// IK, converted with the map's Unity → Minecraft mapping.
    /// </summary>
    public static class GorillaModel
    {
        private const int MaxTexture = 512;

        /// <summary>The local gorilla's default body (with head), or null before the rig exists.</summary>
        public static SkinnedMeshRenderer Body()
        {
            VRRig rig = GorillaTagger.Instance != null ? GorillaTagger.Instance.offlineVRRig : null;
            if (rig == null || rig.bodyRenderer == null)
                return null;
            SkinnedMeshRenderer body = rig.bodyRenderer.GetBody(GorillaBodyType.Default);
            return body != null && body.sharedMesh != null && body.sharedMesh.isReadable ? body : null;
        }

        /// <summary>The GT_MODEL frame, or null if the gorilla can't be read yet.</summary>
        public static byte[] Export(System.Action<string> log)
        {
            SkinnedMeshRenderer body = Body();
            if (body == null)
                return null;
            VRRig rig = GorillaTagger.Instance.offlineVRRig;
            Mesh mesh = body.sharedMesh;
            Transform[] bones = body.bones;
            Matrix4x4[] bindposes = mesh.bindposes;

            var positions = new List<Vector3>(mesh.vertices);
            var normals = new List<Vector3>(mesh.normals.Length == mesh.vertexCount ? mesh.normals : new Vector3[mesh.vertexCount]);
            var uvs = new List<Vector2>(mesh.uv.Length == mesh.vertexCount ? mesh.uv : new Vector2[mesh.vertexCount]);
            var weights = new List<BoneWeight>(mesh.boneWeights);
            var submeshes = new List<(int[] indices, Material material, bool tinted)>();
            Material[] materials = body.sharedMaterials;
            for (int s = 0; s < mesh.subMeshCount; s++)
            {
                Material m = s < materials.Length ? materials[s] : null;
                bool tinted = m != null && (m == rig.myDefaultSkinMaterialInstance || m.name.StartsWith("gorilla_body") || s == 0);
                submeshes.Add((mesh.GetTriangles(s), m, tinted));
            }

            // The face is its own mesh on the head: add it as a submesh skinned fully to its bone.
            MeshRenderer face = rig.faceSkin;
            MeshFilter faceFilter = face != null ? face.GetComponent<MeshFilter>() : null;
            if (faceFilter != null && faceFilter.sharedMesh != null && faceFilter.sharedMesh.isReadable)
            {
                int bone = BoneOf(face.transform, bones);
                if (bone >= 0)
                {
                    Mesh fm = faceFilter.sharedMesh;
                    // Face point → world, then into the bone's bind space, so the bone's skin matrix puts it back.
                    Matrix4x4 toBind = bindposes[bone].inverse * bones[bone].worldToLocalMatrix * face.transform.localToWorldMatrix;
                    Matrix4x4 normalToBind = toBind.inverse.transpose;
                    int start = positions.Count;
                    Vector3[] fv = fm.vertices, fn = fm.normals;
                    Vector2[] fuv = fm.uv;
                    for (int i = 0; i < fv.Length; i++)
                    {
                        positions.Add(toBind.MultiplyPoint3x4(fv[i]));
                        normals.Add(i < fn.Length ? normalToBind.MultiplyVector(fn[i]).normalized : Vector3.up);
                        uvs.Add(i < fuv.Length ? fuv[i] : Vector2.zero);
                        weights.Add(new BoneWeight { boneIndex0 = bone, weight0 = 1f });
                    }
                    int[] tris = fm.triangles;
                    for (int i = 0; i < tris.Length; i++)
                        tris[i] += start;
                    submeshes.Add((tris, face.sharedMaterial, false));
                }
                else
                    log("Gorilla face isn't on a body bone; leaving it out");
            }

            var w = new FrameWriter(Proto.GtModel);
            w.I32(positions.Count);
            for (int i = 0; i < positions.Count; i++)
            {
                Vector3 p = positions[i], n = normals[i];
                Vector2 uv = uvs[i];
                BoneWeight bw = weights[i];
                w.F32(p.x).F32(p.y).F32(p.z).F32(n.x).F32(n.y).F32(n.z).F32(uv.x).F32(uv.y);
                w.U8(bw.boneIndex0).U8(bw.boneIndex1).U8(bw.boneIndex2).U8(bw.boneIndex3);
                w.F32(bw.weight0).F32(bw.weight1).F32(bw.weight2).F32(bw.weight3);
            }
            w.I32(submeshes.Count);
            foreach (var (indices, material, tinted) in submeshes)
            {
                byte[] png = TexturePng(material, out Vector4 st);
                w.I32(indices.Length);
                foreach (int i in indices)
                    w.I32(i);
                w.U8(tinted ? 1 : 0);
                Color c = material != null && material.HasProperty("_BaseColor") && !tinted ? material.GetColor("_BaseColor") : Color.white;
                w.F32(c.r).F32(c.g).F32(c.b);
                w.F32(st.x).F32(st.y).F32(st.z).F32(st.w);
                w.I32(png.Length).Bytes(png);
            }
            w.I32(bones.Length);
            log($"Exported Gorilla Tag's gorilla for Minecraft: {positions.Count} vertices, {submeshes.Count} parts, {bones.Length} bones");
            return w.Finish();
        }

        private static int BoneOf(Transform t, Transform[] bones)
        {
            for (Transform p = t; p != null; p = p.parent)
                for (int i = 0; i < bones.Length; i++)
                    if (bones[i] == p)
                        return i;
            return -1;
        }

        /// <summary>
        /// The material's base texture as a PNG. Gorilla Tag keeps textures in a texture-array
        /// atlas (_BaseMap_Atlas, slice _BaseMap_AtlasSlice, real size in _BaseMap_WH): that slice
        /// is copied out on the GPU, drawn into a readable texture and cropped. st = the material's
        /// UV scale/offset (some faces use half of their texture).
        /// </summary>
        private static byte[] TexturePng(Material m, out Vector4 st)
        {
            st = new Vector4(1, 1, 0, 0);
            Texture2D readable = null;
            RenderTexture rt = null;
            Texture2D slice = null;
            try
            {
                if (m != null && m.HasProperty("_BaseMap"))
                {
                    Vector2 scale = m.GetTextureScale("_BaseMap"), offset = m.GetTextureOffset("_BaseMap");
                    st = new Vector4(scale.x, scale.y, offset.x, offset.y);
                }
                Texture source = null;
                int w = 0, h = 0;
                if (m != null && m.HasProperty("_BaseMap_Atlas") && m.GetTexture("_BaseMap_Atlas") is Texture2DArray atlas)
                {
                    int index = Mathf.Clamp((int)m.GetFloat("_BaseMap_AtlasSlice"), 0, atlas.depth - 1);
                    slice = new Texture2D(atlas.width, atlas.height, atlas.graphicsFormat, 1, TextureCreationFlags.None);
                    Graphics.CopyTexture(atlas, index, 0, slice, 0, 0);
                    source = slice;
                    Vector4 wh = m.HasProperty("_BaseMap_WH") ? m.GetVector("_BaseMap_WH") : Vector4.zero;
                    w = wh.z > 0 ? Mathf.Min((int)wh.z, atlas.width) : atlas.width;
                    h = wh.w > 0 ? Mathf.Min((int)wh.w, atlas.height) : atlas.height;
                    rt = RenderTexture.GetTemporary(atlas.width, atlas.height, 0, RenderTextureFormat.ARGB32, RenderTextureReadWrite.sRGB);
                }
                else if (m != null && (m.HasProperty("_BaseMap") ? m.GetTexture("_BaseMap") : m.mainTexture) is Texture t && t != null)
                {
                    source = t;
                    w = Mathf.Min(t.width, MaxTexture);
                    h = Mathf.Min(t.height, MaxTexture);
                    rt = RenderTexture.GetTemporary(w, h, 0, RenderTextureFormat.ARGB32, RenderTextureReadWrite.sRGB);
                }
                if (source == null)
                    return White();
                var previous = RenderTexture.active;
                Graphics.Blit(source, rt);
                RenderTexture.active = rt;
                readable = new Texture2D(w, h, TextureFormat.RGBA32, false);
                readable.ReadPixels(new Rect(0, 0, w, h), 0, 0);
                readable.Apply(false);
                RenderTexture.active = previous;
                return readable.EncodeToPNG();
            }
            catch (System.Exception e)
            {
                Plugin.Log.LogWarning($"Couldn't read the texture of {m?.name}: {e.Message}");
                return White();
            }
            finally
            {
                if (rt != null)
                    RenderTexture.ReleaseTemporary(rt);
                if (readable != null)
                    Object.Destroy(readable);
                if (slice != null)
                    Object.Destroy(slice);
            }
        }

        private static byte[] White()
        {
            var t = new Texture2D(2, 2, TextureFormat.RGBA32, false);
            t.SetPixels32(new[] { new Color32(255, 255, 255, 255), new Color32(255, 255, 255, 255), new Color32(255, 255, 255, 255), new Color32(255, 255, 255, 255) });
            byte[] png = t.EncodeToPNG();
            Object.Destroy(t);
            return png;
        }

        /// <summary>
        /// GT_BONES: per bone, the 3x4 matrix taking a mesh point to Minecraft coordinates relative to
        /// the head (refX/Y/Z): Unity→Minecraft mapping × bone's local-to-world × bind pose.
        /// </summary>
        public static byte[] Pose(Coords coords, double refX, double refY, double refZ)
        {
            SkinnedMeshRenderer body = Body();
            if (body == null)
                return null;
            Transform[] bones = body.bones;
            Matrix4x4[] bindposes = body.sharedMesh.bindposes;
            // Unity world → Minecraft, relative to the head: p' = (x-Ax)/s + Ox - refX, ..., -(z-Az)/s + Oz - refZ.
            float inv = 1f / coords.Scale;
            Matrix4x4 toMc = Matrix4x4.TRS(new Vector3((float)(coords.OriginX - refX), (float)(coords.OriginY - refY), (float)(coords.OriginZ - refZ)),
                    Quaternion.identity, new Vector3(inv, inv, -inv))
                * Matrix4x4.Translate(-coords.Anchor);
            var w = new FrameWriter(Proto.GtBones);
            w.F64(refX).F64(refY).F64(refZ).I32(bones.Length);
            for (int i = 0; i < bones.Length; i++)
            {
                Matrix4x4 m = bones[i] != null ? toMc * bones[i].localToWorldMatrix * bindposes[i] : Matrix4x4.zero;
                for (int r = 0; r < 3; r++)
                    w.F32(m[r, 0]).F32(m[r, 1]).F32(m[r, 2]).F32(m[r, 3]);
            }
            return w.Finish();
        }
    }
}
